package storage.redis

import akka.http.scaladsl.util.FastFuture._

import com.yubico.u2f.data.DeviceRegistration
import env.Env
import models.U2FAdminDataStore
import org.joda.time.DateTime
import play.api.libs.json.{JsObject, JsValue, Json}
import redis.RedisClientMasterSlaves

import scala.concurrent.{ExecutionContext, Future}

class RedisU2FAdminDataStore(redisCli: RedisClientMasterSlaves) extends U2FAdminDataStore {

  override def deleteUser(username: String, id: String)(implicit ec: ExecutionContext, env: Env): Future[Long] =
    redisCli.hdel(s"opun:u2f:users:$username", id)

  override def hasAlreadyLoggedIn(email: String)(implicit ec: ExecutionContext, env: Env): Future[Boolean] =
    redisCli.sismember(s"opun:users:alreadyloggedin", email)

  override def alreadyLoggedIn(email: String)(implicit ec: ExecutionContext, env: Env): Future[Long] =
    redisCli.sadd(s"opun:users:alreadyloggedin", email)

  override def findAll()(implicit ec: ExecutionContext, env: Env): Future[Seq[JsValue]] =
    redisCli
      .keys("opun:u2f:users:*")
      .fast
      .flatMap(keys => Future.sequence(keys.map(k => redisCli.hgetall(k))))
      .map { m =>
        m.flatMap(_.values).map(j => Json.parse(j.utf8String))
      }

  override def getRequest(id: String)(implicit ec: ExecutionContext, env: Env): Future[Option[String]] =
    redisCli.get(s"opun:u2f:requests:$id").fast.map(_.map(_.utf8String))

  override def addRequest(id: String, regData: String)(implicit ec: ExecutionContext, env: Env): Future[Boolean] =
    redisCli.set(s"opun:u2f:requests:$id", regData, pxMilliseconds = Some(60000))

  override def deleteRequest(id: String)(implicit ec: ExecutionContext, env: Env): Future[Boolean] =
    redisCli.del(s"opun:u2f:requests:$id").fast.map(_ > 0)

  override def registerUser(username: String, password: String, label: String, reg: DeviceRegistration)(
      implicit ec: ExecutionContext,
      env: Env
  ): Future[Boolean] =
    redisCli.hset(
      s"opun:u2f:users:$username",
      reg.getKeyHandle,
      Json.stringify(
        Json.obj(
          "username"     -> username,
          "label"        -> label,
          "password"     -> password,
          "createdAt"    -> DateTime.now(),
          "registration" -> Json.parse(reg.toJson)
        )
      )
    )

  override def getUserRegistration(username: String)(implicit ec: ExecutionContext,
                                                     env: Env): Future[Seq[(DeviceRegistration, JsValue)]] =
    redisCli
      .hgetall(s"opun:u2f:users:$username")
      .fast
      .map(_.values)
      .map(
        values =>
          values
            .map(bs => Json.parse(bs.utf8String))
            .map(js => (Json.stringify((js \ "registration").as[JsObject]), js))
            .map(t => (DeviceRegistration.fromJson(t._1), t._2))
            .toSeq
      )

}
