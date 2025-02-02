package controllers

import java.util.Base64

import actions.{BackOfficeAction, BackOfficeActionAuth}
import akka.http.scaladsl.util.FastFuture
import akka.stream.scaladsl.Source
import akka.util.ByteString
import ch.qos.logback.classic.{Level, LoggerContext}
import com.google.common.base.Charsets
import env.Env
import events._
import gateway.{GatewayRequestHandler, WebSocketHandler}
import models._
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import play.api.Logger
import play.api.http.HttpEntity
import play.api.libs.json._
import play.api.libs.streams.Accumulator
import play.api.libs.ws.StreamedBody
import play.api.mvc._
import utils.LocalCache
import security._
import org.mindrot.jbcrypt.BCrypt

import scala.concurrent.Future
import scala.util.Success

class BackOfficeController(BackOfficeAction: BackOfficeAction, BackOfficeActionAuth: BackOfficeActionAuth)(
    implicit env: Env
) extends Controller {

  implicit lazy val ec  = env.backOfficeExecutionContext
  implicit lazy val lat = env.materializer

  lazy val logger = Logger("otoroshi-backoffice-api")

  lazy val commitVersion = Option(System.getenv("COMMIT_ID")).getOrElse("--")

  val sourceBodyParser = BodyParser("BackOfficeApi BodyParser") { _ =>
    Accumulator.source[ByteString].map(Right.apply)
  }

  def proxyAdminApi(path: String) = BackOfficeActionAuth.async(sourceBodyParser) { ctx =>
    val host     = if (env.isDev) env.adminApiExposedHost else env.adminApiExposedHost
    val localUrl = if (env.adminApiProxyHttps) s"https://127.0.0.1:${env.port}" else s"http://127.0.0.1:${env.port}"
    val url      = if (env.adminApiProxyUseLocal) localUrl else s"https://${env.adminApiExposedHost}"
    logger.debug(s"Calling ${ctx.request.method} $url/$path with Host = $host")
    env.Ws
      .url(s"$url/$path")
      .withHeaders(
        "Host"                       -> host,
        env.Headers.OpunVizFromLabel -> "Otoroshi Admin UI",
        env.Headers.OpunVizFrom      -> "otoroshi-admin-ui",
        env.Headers.OpunClientId     -> env.backOfficeApiKey.clientId,
        env.Headers.OpunClientSecret -> env.backOfficeApiKey.clientSecret,
        env.Headers.OpunAdminProfile -> Base64.getUrlEncoder.encodeToString(
          Json.stringify(ctx.user.profile).getBytes(Charsets.UTF_8)
        ),
        "Accept"       -> "application/json",
        "Content-Type" -> "application/json"
      )
      .withFollowRedirects(false)
      .withMethod(ctx.request.method)
      .withQueryString(ctx.request.queryString.toSeq.map(t => (t._1, t._2.head)): _*)
      .withBody(StreamedBody(ctx.request.body))
      .stream()
      .map { res =>
        val ctype = res.headers.headers.get("Content-Type").flatMap(_.headOption).getOrElse("application/json")
        Status(res.headers.status)
          .sendEntity(HttpEntity.Streamed(res.body, None, Some(ctype)))
          .withHeaders(res.headers.headers.mapValues(_.head).toSeq.filter(_._1 != "Content-Type"): _*)
          .as(ctype)
      }
  }

  def version = BackOfficeAction {
    Ok(Json.obj("version" -> commitVersion))
  }

  def getEnv() = BackOfficeAction.async { ctx =>
    val hash = BCrypt.hashpw("password", BCrypt.gensalt())
    env.datastores.globalConfigDataStore.singleton().flatMap { config =>
      env.datastores.simpleAdminDataStore.findAll().map { users =>
        val changePassword = users.filter { user =>
          //(user \ "password").as[String] == hash &&
          (user \ "username").as[String] == "admin@otoroshi.io"
        }.nonEmpty
        Ok(
          Json.obj(
            "changePassword"     -> changePassword,
            "mailgun"            -> config.mailGunSettings.isDefined,
            "clevercloud"        -> config.cleverSettings.isDefined,
            "apiReadOnly"        -> config.apiReadOnly,
            "u2fLoginOnly"       -> config.u2fLoginOnly,
            "env"                -> env.env,
            "redirectToDev"      -> env.redirectToDev,
            "displayPrivateApps" -> config.privateAppsAuth0Config.isDefined,
            "clientIdHeader"     -> env.Headers.OpunClientId,
            "clientSecretHeader" -> env.Headers.OpunClientSecret
          )
        )
      }
    }
  }

  def index = BackOfficeAction.async { ctx =>
    env.datastores.globalConfigDataStore.singleton().map { config =>
      Ok(
        views.html.backoffice
          .index(!(config.u2fLoginOnly || config.backofficeAuth0Config.isEmpty), ctx.user, ctx.request, env)
      )
    }
  }

  def dashboard = BackOfficeActionAuth.async { ctx =>
    env.datastores.globalConfigDataStore.singleton().map { config =>
      Ok(views.html.backoffice.dashboard(ctx.user, config, env))
    }
  }

  def dashboardRoutes(ui: String) = BackOfficeActionAuth.async { ctx =>
    import scala.concurrent.duration._
    env.datastores.globalConfigDataStore.singleton().map { config =>
      Ok(views.html.backoffice.dashboard(ctx.user, config, env))
    }
  }

  def error(message: Option[String]) = BackOfficeAction { ctx =>
    Ok(views.html.opunapps.error(message.getOrElse("Error message"), env))
  }

  def documentationFrame(lineId: String, serviceId: String) = BackOfficeActionAuth.async { ctx =>
    env.datastores.serviceDescriptorDataStore.findById(serviceId).map {
      case Some(descriptor) => Ok(views.html.backoffice.documentationframe(descriptor, env))
      case None             => NotFound(Json.obj("error" -> s"Service with id $serviceId not found"))
    }
  }

  def documentationFrameDescriptor(lineId: String, serviceId: String) = BackOfficeActionAuth.async {
    import scala.concurrent.duration._
    env.datastores.serviceDescriptorDataStore.findById(serviceId).flatMap {
      case Some(service) if service.api.openApiDescriptorUrl.isDefined => {
        val state = IdGenerator.extendedToken(128)
        val claim = OpunClaim(
          iss = env.Headers.OpunGateway,
          sub = "Documentation",
          aud = service.name,
          exp = DateTime.now().plusSeconds(30).toDate.getTime,
          iat = DateTime.now().toDate.getTime,
          jti = IdGenerator.uuid
        ).serialize(env)
        val url = service.api.openApiDescriptorUrl.get match {
          case uri if uri.startsWith("/") => s"${service.target.scheme}://${service.target.host}${uri}"
          case url                        => url
        }
        env.Ws
          .url(url)
          .withRequestTimeout(10.seconds)
          .withHeaders(
            env.Headers.OpunGatewayRequestId -> env.snowflakeGenerator.nextId().toString,
            env.Headers.OpunGatewayState     -> state,
            env.Headers.OpunGatewayClaim     -> claim
          )
          .get()
          .map { resp =>
            try {
              val swagger = (resp.json.as[JsObject] \ "swagger").as[String]
              swagger match {
                case "2.0" => Ok(Json.prettyPrint(resp.json)).as("application/json")
                case "3.0" => Ok(Json.prettyPrint(resp.json)).as("application/json")
                case _     => InternalServerError(views.html.opunapps.error(s"Swagger version $swagger not supported", env))
              }
            } catch {
              case e: Throwable => InternalServerError(Json.obj("error" -> e.getMessage))
            }
          }
      }
      case _ => FastFuture.successful(NotFound(views.html.opunapps.error("Service not found", env)))
    }
  }

  def searchServicesApi() = BackOfficeActionAuth.async(parse.json) { ctx =>
    val query = (ctx.request.body \ "query").asOpt[String].getOrElse("--").toLowerCase()
    Audit.send(
      BackOfficeEvent(env.snowflakeGenerator.nextId().toString,
                      env.env,
                      ctx.user,
                      "SERVICESEARCH",
                      "user searched for a service",
                      ctx.from,
                      Json.obj(
                        "query" -> query
                      ))
    )
    val fu: Future[Seq[ServiceDescriptor]] =
      Option(LocalCache.allServices.getIfPresent("all")).map(_.asInstanceOf[Seq[ServiceDescriptor]]) match {
        case Some(descriptors) => FastFuture.successful(descriptors)
        case None =>
          env.datastores.serviceDescriptorDataStore.findAll().andThen {
            case Success(seq) => LocalCache.allServices.put("all", seq)
          }
      }
    fu.map { services =>
      val filtered = services.filter { service =>
        service.id.toLowerCase() == query || service.name.toLowerCase().contains(query) || service.env
          .toLowerCase()
          .contains(query)
      }
      Ok(JsArray(filtered.map(s => Json.obj("serviceId" -> s.id, "name" -> s.name, "env" -> s.env))))
    }
  }

  def cleverApps() = BackOfficeActionAuth.async { ctx =>
    val paginationPage: Int = ctx.request.queryString.get("page").flatMap(_.headOption).map(_.toInt).getOrElse(1)
    val paginationPageSize: Int =
      ctx.request.queryString.get("pageSize").flatMap(_.headOption).map(_.toInt).getOrElse(Int.MaxValue)
    val paginationPosition = (paginationPage - 1) * paginationPageSize
    env.datastores.globalConfigDataStore.singleton().flatMap { globalConfig =>
      env.datastores.serviceDescriptorDataStore.findAll().flatMap { services =>
        globalConfig.cleverClient match {
          case Some(client) => {
            client.apps(client.orgaId).map { cleverapps =>
              val apps = cleverapps.value
                .map { app =>
                  val id                 = (app \ "id").as[String]
                  val name               = (app \ "name").as[String]
                  val hosts: Seq[String] = (app \ "vhosts").as[JsArray].value.map(vhost => (vhost \ "fqdn").as[String])
                  val preferedHost       = hosts.filterNot(h => h.contains("cleverapps.io")).headOption.getOrElse(hosts.head)
                  val service            = services.find(s => s.targets.exists(t => hosts.contains(t.host)))
                  Json.obj(
                    "name"    -> (app \ "name").as[String],
                    "id"      -> id,
                    "url"     -> s"https://${preferedHost}/",
                    "console" -> s"https://console.clever-cloud.com/organisations/${client.orgaId}/applications/$id",
                    "exists"  -> service.isDefined,
                    "host"    -> preferedHost,
                    "otoUrl"  -> s"/lines/${service.map(_.env).getOrElse("--")}/services/${service.map(_.id).getOrElse("--")}"
                  )
                }
                .drop(paginationPosition)
                .take(paginationPageSize)
              Ok(JsArray(apps))
            }
          }
          case None => FastFuture.successful(Ok(Json.arr()))
        }
      }
    }
  }

  def sessions() = BackOfficeActionAuth.async { ctx =>
    val paginationPage: Int = ctx.request.queryString.get("page").flatMap(_.headOption).map(_.toInt).getOrElse(1)
    val paginationPageSize: Int =
      ctx.request.queryString.get("pageSize").flatMap(_.headOption).map(_.toInt).getOrElse(Int.MaxValue)
    val paginationPosition = (paginationPage - 1) * paginationPageSize
    env.datastores.backOfficeUserDataStore.sessions() map { sessions =>
      Ok(JsArray(sessions.drop(paginationPosition).take(paginationPageSize)))
    }
  }

  def discardSession(id: String) = BackOfficeActionAuth.async { ctx =>
    env.datastores.globalConfigDataStore.singleton().filter(!_.apiReadOnly).flatMap { _ =>
      env.datastores.backOfficeUserDataStore.discardSession(id) map { _ =>
        val event = BackOfficeEvent(
          env.snowflakeGenerator.nextId().toString,
          env.env,
          ctx.user,
          "DISCARD_SESSION",
          s"Admin discarded an Admin session",
          ctx.from,
          Json.obj("sessionId" -> id)
        )
        Audit.send(event)
        Alerts.send(SessionDiscardedAlert(env.snowflakeGenerator.nextId().toString, env.env, ctx.user, event))
        Ok(Json.obj("done" -> true))
      }
    } recover {
      case _ => Ok(Json.obj("done" -> false))
    }
  }

  def discardAllSessions() = BackOfficeActionAuth.async { ctx =>
    env.datastores.globalConfigDataStore.singleton().filter(!_.apiReadOnly).flatMap { _ =>
      env.datastores.backOfficeUserDataStore.discardAllSessions() map { _ =>
        val event = BackOfficeEvent(
          env.snowflakeGenerator.nextId().toString,
          env.env,
          ctx.user,
          "DISCARD_SESSIONS",
          s"Admin discarded Admin sessions",
          ctx.from,
          Json.obj()
        )
        Audit.send(event)
        Alerts.send(SessionsDiscardedAlert(env.snowflakeGenerator.nextId().toString, env.env, ctx.user, event))
        Ok(Json.obj("done" -> true))
      }
    } recover {
      case _ => Ok(Json.obj("done" -> false))
    }
  }

  def privateAppsSessions() = BackOfficeActionAuth.async { ctx =>
    val paginationPage: Int = ctx.request.queryString.get("page").flatMap(_.headOption).map(_.toInt).getOrElse(1)
    val paginationPageSize: Int =
      ctx.request.queryString.get("pageSize").flatMap(_.headOption).map(_.toInt).getOrElse(Int.MaxValue)
    val paginationPosition = (paginationPage - 1) * paginationPageSize
    env.datastores.privateAppsUserDataStore.findAll() map { sessions =>
      Ok(JsArray(sessions.drop(paginationPosition).take(paginationPageSize).map(_.toJson)))
    }
  }

  def discardPrivateAppsSession(id: String) = BackOfficeActionAuth.async { ctx =>
    env.datastores.globalConfigDataStore.singleton().filter(!_.apiReadOnly).flatMap { _ =>
      env.datastores.privateAppsUserDataStore.delete(id) map { _ =>
        val event = BackOfficeEvent(
          env.snowflakeGenerator.nextId().toString,
          env.env,
          ctx.user,
          "DISCARD_PRIVATE_APPS_SESSION",
          s"Admin discarded a private app session",
          ctx.from,
          Json.obj("sessionId" -> id)
        )
        Audit.send(event)
        Alerts.send(SessionDiscardedAlert(env.snowflakeGenerator.nextId().toString, env.env, ctx.user, event))
        Ok(Json.obj("done" -> true))
      }
    } recover {
      case _ => Ok(Json.obj("done" -> false))
    }
  }

  def discardAllPrivateAppsSessions() = BackOfficeActionAuth.async { ctx =>
    env.datastores.globalConfigDataStore.singleton().filter(!_.apiReadOnly).flatMap { _ =>
      env.datastores.privateAppsUserDataStore.deleteAll() map { _ =>
        val event = BackOfficeEvent(
          env.snowflakeGenerator.nextId().toString,
          env.env,
          ctx.user,
          "DISCARD_PRIVATE_APPS_SESSIONS",
          s"Admin discarded private apps sessions",
          ctx.from,
          Json.obj()
        )
        Audit.send(event)
        Alerts.send(SessionsDiscardedAlert(env.snowflakeGenerator.nextId().toString, env.env, ctx.user, event))
        Ok(Json.obj("done" -> true))
      }
    } recover {
      case _ => Ok(Json.obj("done" -> false))
    }
  }

  def panicMode() = BackOfficeActionAuth.async { ctx =>
    env.datastores.globalConfigDataStore.singleton().filter(!_.apiReadOnly).flatMap { c =>
      c.copy(u2fLoginOnly = true, apiReadOnly = true).save()
    } flatMap { _ =>
      env.datastores.backOfficeUserDataStore.discardAllSessions()
    } map { _ =>
      val event = BackOfficeEvent(
        env.snowflakeGenerator.nextId().toString,
        env.env,
        ctx.user,
        "ACTIVATE_PANIC_MODE",
        s"Admin activated panic mode",
        ctx.from,
        Json.obj()
      )
      Audit.send(event)
      Alerts.send(PanicModeAlert(env.snowflakeGenerator.nextId().toString, env.env, ctx.user, event))
      Ok(Json.obj("done" -> true))
    } recover {
      case _ => Ok(Json.obj("done" -> false))
    }
  }

  def auditEvents() = BackOfficeActionAuth.async { ctx =>
    val paginationPage: Int = ctx.request.queryString.get("page").flatMap(_.headOption).map(_.toInt).getOrElse(1)
    val paginationPageSize: Int =
      ctx.request.queryString.get("pageSize").flatMap(_.headOption).map(_.toInt).getOrElse(Int.MaxValue)
    val paginationPosition = (paginationPage - 1) * paginationPageSize
    env.datastores.auditDataStore.findAllRaw().map { elems =>
      val filtered = elems.drop(paginationPosition).take(paginationPageSize)
      Ok.chunked(
          Source
            .single(ByteString("["))
            .concat(
              Source
                .apply(scala.collection.immutable.Iterable.empty[ByteString] ++ filtered)
                .intersperse(ByteString(","))
            )
            .concat(Source.single(ByteString("]")))
        )
        .as("application/json")
    }
  }

  def alertEvents() = BackOfficeActionAuth.async { ctx =>
    val paginationPage: Int = ctx.request.queryString.get("page").flatMap(_.headOption).map(_.toInt).getOrElse(1)
    val paginationPageSize: Int =
      ctx.request.queryString.get("pageSize").flatMap(_.headOption).map(_.toInt).getOrElse(Int.MaxValue)
    val paginationPosition = (paginationPage - 1) * paginationPageSize
    env.datastores.alertDataStore.findAllRaw().map { elems =>
      val filtered = elems.drop(paginationPosition).take(paginationPageSize)
      Ok.chunked(
          Source
            .single(ByteString("["))
            .concat(
              Source
                .apply(scala.collection.immutable.Iterable.empty[ByteString] ++ filtered)
                .intersperse(ByteString(","))
            )
            .concat(Source.single(ByteString("]")))
        )
        .as("application/json")
    }
  }

  def changeLogLevel(name: String, newLevel: Option[String]) = BackOfficeActionAuth { ctx =>
    val loggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    val _logger       = loggerContext.getLogger(name)
    val oldLevel      = Option(_logger.getLevel).map(_.levelStr).getOrElse(Level.OFF.levelStr)
    _logger.setLevel(newLevel.map(v => Level.valueOf(v)).getOrElse(Level.ERROR))
    Ok(Json.obj("name" -> name, "oldLevel" -> oldLevel, "newLevel" -> _logger.getLevel.levelStr))
  }

  def getLogLevel(name: String) = BackOfficeActionAuth { ctx =>
    val loggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    val _logger       = loggerContext.getLogger(name)
    Ok(Json.obj("name" -> name, "level" -> _logger.getLevel.levelStr))
  }

  def getAllLoggers() = BackOfficeActionAuth { ctx =>
    import collection.JavaConversions._

    val paginationPage: Int = ctx.request.queryString.get("page").flatMap(_.headOption).map(_.toInt).getOrElse(1)
    val paginationPageSize: Int =
      ctx.request.queryString.get("pageSize").flatMap(_.headOption).map(_.toInt).getOrElse(Int.MaxValue)
    val paginationPosition = (paginationPage - 1) * paginationPageSize

    val loggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    val rawLoggers    = loggerContext.getLoggerList.toIndexedSeq.drop(paginationPosition).take(paginationPageSize)
    val loggers = JsArray(rawLoggers.map(logger => {
      val level: String = Option(logger.getLevel).map(_.levelStr).getOrElse("OFF")
      Json.obj("name" -> logger.getName, "level" -> level)
    }))
    Ok(loggers)
  }

  case class ServiceRate(rate: Double, name: String, id: String)

  def mostCalledServices() = BackOfficeActionAuth.async { ctx =>
    val paginationPage: Int = ctx.request.queryString.get("page").flatMap(_.headOption).map(_.toInt).getOrElse(1)
    val paginationPageSize: Int =
      ctx.request.queryString.get("pageSize").flatMap(_.headOption).map(_.toInt).getOrElse(10)
    val paginationPosition = (paginationPage - 1) * paginationPageSize

    env.datastores.serviceDescriptorDataStore.findAll().flatMap { services =>
      Future.sequence(
        services.map(
          s => env.datastores.serviceDescriptorDataStore.callsPerSec(s.id).map(rate => ServiceRate(rate, s.name, s.id))
        )
      )
    } map { items =>
      items.sortWith(_.rate > _.rate).drop(paginationPosition).take(paginationPageSize)
    } map { items =>
      items.map { i =>
        val value: Double = Option(i.rate).filterNot(_.isInfinity).getOrElse(0.0)
        Json.obj(
          "rate" -> value,
          "name" -> i.name,
          "id"   -> i.id
        )
      }
    } map { items =>
      Ok(JsArray(items))
    }
  }

  def servicesMap() = BackOfficeActionAuth.async { ctx =>
    env.datastores.serviceGroupDataStore.findAll().flatMap { groups =>
      Future.sequence(
        groups.map { group =>
          env.datastores.serviceDescriptorDataStore.findByGroup(group.id).flatMap { services =>
            Future.sequence(services.map { service =>
              env.datastores.serviceDescriptorDataStore.callsPerSec(service.id).map(cps => (service, cps))
            })
          } map {
            case services if services.isEmpty => Json.obj()
            case services if services.nonEmpty =>
              Json.obj(
                "name" -> group.name,
                "children" -> JsArray(services.map {
                  case (service, cps) =>
                    val size: Int = ((1.0 + cps) * 1000.0).toInt
                    Json.obj(
                      "name" -> service.name,
                      "env"  -> service.env,
                      "id"   -> service.id,
                      "size" -> size
                    )
                })
              )
          }
        }
      )
    } map { children =>
      Json.obj("name" -> "Otoroshi Services", "children" -> children.filterNot(_ == Json.obj()))
    } map { json =>
      Ok(json)
    }
  }

  def resetCircuitBreakers(id: String) = BackOfficeActionAuth { ctx =>
    env.circuitBeakersHolder.resetCircuitBreakersFor(id)
    Ok(Json.obj("done" -> true))
  }
}
