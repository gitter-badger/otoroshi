package controllers

import env.Env
import play.api.Logger
import play.api.libs.json._
import play.api.mvc._

object Implicits {
  implicit class EnhancedJsValue(val value: JsValue) extends AnyVal {
    def ~~>(description: String): JsValue =
      value.as[JsObject] ++ Json.obj(
        "description" -> description
      )
  }
}

class SwaggerController()(implicit env: Env) extends Controller {

  import Implicits._

  implicit lazy val ec = env.apiExecutionContext

  lazy val logger = Logger("otoroshi-swagger-controller")

  def swagger = Action { req =>
    Ok(Json.prettyPrint(swaggerDescriptor())).as("application/json").withHeaders("Access-Control-Allow-Origin" -> "*")
  }

  def swaggerUi = Action { req =>
    Ok(views.html.opunapps.documentationframe(s"${env.exposedRootScheme}://${env.backOfficeHost}/api/swagger.json"))
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  // Types definitions

  def SimpleObjectType =
    Json.obj("type"                 -> "object",
             "required"             -> Json.arr(),
             "example"              -> Json.obj("key" -> "value"),
             "additionalProperties" -> Json.obj("type" -> "string"))
  def SimpleStringType   = Json.obj("type" -> "string", "example"  -> "a string value")
  def SimpleDoubleType   = Json.obj("type" -> "integer", "format"  -> "double", "example" -> 42.2)
  def OptionalStringType = Json.obj("type" -> "string", "required" -> false, "example" -> "a string value")
  def SimpleBooleanType  = Json.obj("type" -> "boolean", "example" -> true)
  def SimpleDateType     = Json.obj("type" -> "string", "format"   -> "date", "example" -> "2017-07-21")
  def SimpleDateTimeType = Json.obj("type" -> "string", "format"   -> "date-time", "example" -> "2017-07-21T17:32:28Z")
  def SimpleLongType     = Json.obj("type" -> "integer", "format"  -> "int64", "example" -> 123)
  def SimpleIntType      = Json.obj("type" -> "integer", "format"  -> "int32", "example" -> 123123)
  def SimpleHostType     = Json.obj("type" -> "string", "format"   -> "hostname", "example" -> "www.google.com")
  def SimpleIpv4Type     = Json.obj("type" -> "string", "format"   -> "ipv4", "example" -> "192.192.192.192")
  def SimpleUriType      = Json.obj("type" -> "string", "format"   -> "uri", "example" -> "http://www.google.com")
  def SimpleEmailType    = Json.obj("type" -> "string", "format"   -> "email", "example" -> "admin@otoroshi.io")
  def SimpleUuidType =
    Json.obj("type" -> "string", "format" -> "uuid", "example" -> "110e8400-e29b-11d4-a716-446655440000")
  def Ref(name: String): JsObject = Json.obj("$ref" -> s"#/definitions/$name")
  def ArrayOf(ref: JsValue) = Json.obj(
    "type"  -> "array",
    "items" -> ref
  )

  def RequestBody(typ: JsValue) = Json.obj(
    "required" -> true,
    "content" -> Json.obj(
      "application/json" -> Json.obj(
        "schema" -> typ
      )
    )
  )

  def FormBody(typ: JsValue) = Json.obj(
    "required" -> true,
    "content" -> Json.obj(
      "application/x-www-form-urlencoded" -> Json.obj(
        "schema" -> typ
      )
    )
  )

  def PathParam(name: String, desc: String) = Json.obj(
    "in"          -> "path",
    "name"        -> name,
    "required"    -> true,
    "type"        -> "string",
    "description" -> desc
  )

  def BodyParam(desc: String, typ: JsValue) = Json.obj(
    "in"          -> "body",
    "name"        -> "body",
    "required"    -> true,
    "schema"      -> typ,
    "description" -> desc
  )

  def GoodResponse(ref: JsValue) = Json.obj(
    "description" -> "Successful operation",
    "schema"      -> ref
  )

  def Tag(name: String, description: String) = Json.obj(
    "name"        -> name,
    "description" -> description
  )

  def Operation(
      summary: String,
      tag: String,
      description: String = "",
      operationId: String = "",
      produces: JsArray = Json.arr("application/json"),
      parameters: JsArray = Json.arr(),
      goodCode: String = "200",
      goodResponse: JsObject
  ): JsValue =
    Json.obj(
      "deprecated"  -> false,
      "tags"        -> Json.arr(tag),
      "summary"     -> summary,
      "description" -> description,
      "operationId" -> operationId,
      "produces"    -> produces,
      "parameters"  -> parameters,
      "responses" -> Json.obj(
        "401" -> Json.obj(
          "description" -> "You have to provide an Api Key. Api Key can be passed with 'Otoroshi-Client-Id' and 'Otoroshi-Client-Secret' headers, or use basic http authentication"
        ),
        "400" -> Json.obj(
          "description" -> "Bad resource format. Take another look to the swagger, or open an issue :)"
        ),
        "404" -> Json.obj(
          "description" -> "Resource not found or does not exist"
        ),
        goodCode -> goodResponse
      ),
      "security" -> Json.arr(
        Json.obj(
          "otoroshi_auth" -> Json.arr(
            "write:admins",
            "read:admins"
          )
        )
      )
    )

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  // Models definition

  def Target = Json.obj(
    "description" -> "A Target is where an HTTP call will be forwarded in the end from a service domain",
    "type"        -> "object",
    "required"    -> Json.arr("host", "scheme"),
    "properties" -> Json.obj(
      "host"   -> SimpleHostType ~~> "The host on which the HTTP call will be forwarded. Can be a domain name, or an IP address. Can also have a port",
      "scheme" -> SimpleStringType ~~> "The protocol used for communication. Can be http or https"
    )
  )

  def IpFiltering = Json.obj(
    "description" -> "The filtering configuration block for a service of globally.",
    "type"        -> "object",
    "required"    -> Json.arr("whitelist", "blacklist"),
    "properties" -> Json.obj(
      "whitelist" -> ArrayOf(SimpleIpv4Type) ~~> "Whitelisted IP addresses",
      "blacklist" -> ArrayOf(SimpleIpv4Type) ~~> "Blacklisted IP addresses"
    )
  )

  def ExposedApi = Json.obj(
    "description" -> "The Open API configuration for your service (if one)",
    "type"        -> "object",
    "required"    -> Json.arr("exposeApi"),
    "properties" -> Json.obj(
      "exposeApi"            -> SimpleBooleanType ~~> "Whether or not the current service expose an API with an Open API descriptor",
      "openApiDescriptorUrl" -> SimpleUriType ~~> "The URL of the Open API descriptor"
    )
  )

  def HealthCheck = Json.obj(
    "description" -> "The configuration for checking health of a service. Otoroshi will perform GET call on the URL to check if the service is still alive",
    "type"        -> "object",
    "required"    -> Json.arr("enabled"),
    "properties" -> Json.obj(
      "enabled" -> SimpleBooleanType ~~> "Whether or not healthcheck is enabled on the current service descriptor",
      "url"     -> SimpleUriType ~~> "The URL to check"
    )
  )

  def StatsdConfig = Json.obj(
    "description" -> "The configuration for statsd metrics push",
    "type"        -> "object",
    "required"    -> Json.arr("host", "port", "datadog"),
    "properties" -> Json.obj(
      "host"    -> SimpleStringType ~~> "The host of the StatsD agent",
      "port"    -> SimpleIntType ~~> "The port of the StatsD agent",
      "datadog" -> SimpleBooleanType ~~> "Datadog agent"
    )
  )

  def ClientConfig = Json.obj(
    "description" -> "The configuration of the circuit breaker for a service descriptor",
    "type"        -> "object",
    "required" -> Json.arr("useCircuitBreaker",
                           "retries",
                           "maxErrors",
                           "retryInitialDelay",
                           "backoffFactor",
                           "callTimeout",
                           "globalTimeout",
                           "sampleInterval"),
    "properties" -> Json.obj(
      "useCircuitBreaker" -> SimpleBooleanType ~~> "Use a circuit breaker to avoid cascading failure when calling chains of services. Highly recommended !",
      "retries"           -> SimpleIntType ~~> "Specify how many times the client will try to fetch the result of the request after an error before giving up.",
      "maxErrors"         -> SimpleIntType ~~> "Specify how many errors can pass before opening the circuit breaker",
      "retryInitialDelay" -> SimpleIntType ~~> "Specify the delay between two retries. Each retry, the delay is multiplied by the backoff factor",
      "backoffFactor"     -> SimpleIntType ~~> "Specify the factor to multiply the delay for each retry",
      "callTimeout"       -> SimpleIntType ~~> "Specify how long each call should last at most in milliseconds",
      "globalTimeout"     -> SimpleIntType ~~> "Specify how long the global call (with retries) should last at most in milliseconds",
      "sampleInterval"    -> SimpleIntType ~~> "Specify the sliding window time for the circuit breaker in milliseconds, after this time, error count will be reseted"
    )
  )

  def Canary = Json.obj(
    "description" -> "The configuration of the canary mode for a service descriptor",
    "type"        -> "object",
    "required"    -> Json.arr("enabled", "traffic", "targets", "root"),
    "properties" -> Json.obj(
      "enabled" -> SimpleBooleanType ~~> "Use canary mode for this service",
      "traffic" -> SimpleIntType ~~> "Ratio of traffic that will be sent to canary targets.",
      "targets" -> ArrayOf(Ref("Target")) ~~> "The list of target that Otoroshi will proxy and expose through the subdomain defined before. Otoroshi will do round-robin load balancing between all those targets with circuit breaker mecanism to avoid cascading failures",
      "root"    -> SimpleStringType ~~> "Otoroshi will append this root to any target choosen. If the specified root is '/api/foo', then a request to https://yyyyyyy/bar will actually hit https://xxxxxxxxx/api/foo/bar"
    )
  )

  def Service = Json.obj(
    "description" -> "An otoroshi service descriptor. Represent a forward HTTP call on a domain to another location with some optional api management mecanism",
    "type"        -> "object",
    "required" -> Json.arr(
      "id",
      "groupId",
      "name",
      "env",
      "domain",
      "subdomain",
      "targets",
      "root",
      "enabled",
      "privateApp",
      "forceHttps",
      "maintenanceMode",
      "buildMode",
      "enforceSecureCommunication"
    ),
    "properties" -> Json.obj(
      "id"                         -> SimpleUuidType ~~> "A unique random string to identify your service",
      "groupId"                    -> SimpleStringType ~~> "Each service descriptor is attached to a group. A group can have one or more services. Each API key is linked to a group and allow access to every service in the group",
      "name"                       -> SimpleStringType ~~> "The name of your service. Only for debug and human readability purposes",
      "env"                        -> SimpleStringType ~~> "The line on which the service is available. Based on that value, the name of the line will be appended to the subdomain. For line prod, nothing will be appended. For example, if the subdomain is 'foo' and line is 'preprod', then the exposed service will be available at 'foo.preprod.mydomain'",
      "domain"                     -> SimpleStringType ~~> "The domain on which the service is available.",
      "subdomain"                  -> SimpleStringType ~~> "The subdomain on which the service is available",
      "targets"                    -> ArrayOf(Ref("Target")) ~~> "The list of target that Otoroshi will proxy and expose through the subdomain defined before. Otoroshi will do round-robin load balancing between all those targets with circuit breaker mecanism to avoid cascading failures",
      "root"                       -> SimpleStringType ~~> "Otoroshi will append this root to any target choosen. If the specified root is '/api/foo', then a request to https://yyyyyyy/bar will actually hit https://xxxxxxxxx/api/foo/bar",
      "matchingRoot"               -> OptionalStringType ~~> "The root path on which the service is available",
      "localHost"                  -> SimpleStringType ~~> "The host used localy, mainly localhost:xxxx",
      "localScheme"                -> SimpleStringType ~~> "The scheme used localy, mainly http",
      "redirectToLocal"            -> SimpleBooleanType ~~> "If you work locally with Otoroshi, you may want to use that feature to redirect one particuliar service to a local host. For example, you can relocate https://foo.preprod.bar.com to http://localhost:8080 to make some tests",
      "enabled"                    -> SimpleBooleanType ~~> "Activate or deactivate your service. Once disabled, users will get an error page saying the service does not exist",
      "privateApp"                 -> SimpleBooleanType ~~> "When enabled, user will be allowed to use the service (UI) only if they are registered users of the private apps domain",
      "forceHttps"                 -> SimpleBooleanType ~~> "Will force redirection to https:// if not present",
      "maintenanceMode"            -> SimpleBooleanType ~~> "Display a maintainance page when a user try to use the service",
      "buildMode"                  -> SimpleBooleanType ~~> "Display a construction page when a user try to use the service",
      "enforceSecureCommunication" -> SimpleBooleanType ~~> "When enabled, Otoroshi will try to exchange headers with downstream service to ensure no one else can use the service from outside",
      "secComExcludedPatterns"     -> ArrayOf(SimpleStringType) ~~> "URI patterns excluded from secured communications",
      "publicPatterns"             -> ArrayOf(SimpleStringType) ~~> "By default, every services are private only and you'll need an API key to access it. However, if you want to expose a public UI, you can define one or more public patterns (regex) to allow access to anybody. For example if you want to allow anybody on any URL, just use '/.*'",
      "privatePatterns"            -> ArrayOf(SimpleStringType) ~~> "If you define a public pattern that is a little bit too much, you can make some of public URL private again",
      "ipFiltering"                -> Ref("IpFiltering"),
      "api"                        -> Ref("ExposedApi"),
      "healthCheck"                -> Ref("HealthCheck"),
      "clientConfig"               -> Ref("ClientConfig"),
      "Canary"                     -> Ref("Canary"),
      "statsdConfig"               -> Ref("StatsdConfig"),
      "metadata"                   -> SimpleObjectType ~~> "Just a bunch of random properties",
      "matchingHeaders"            -> SimpleObjectType ~~> "Specify headers that MUST be present on client request to route it. Useful to implement versioning",
      "additionalHeaders"          -> SimpleObjectType ~~> "Specify headers that will be added to each client request. Useful to add authentication"
    )
  )

  def ApiKey = Json.obj(
    "description" -> "An Otoroshi Api Key. An Api Key is defined for a group of services to allow usage of the same Api Key for multiple services.",
    "type"        -> "object",
    "required"    -> Json.arr("clientId", "clientSecret", "clientName", "authorizedGroup", "enabled"),
    "properties" -> Json.obj(
      "clientId"        -> SimpleStringType ~~> "The unique id of the Api Key. Usually 16 random alpha numerical characters, but can be anything",
      "clientSecret"    -> SimpleStringType ~~> "The secret of the Api Key. Usually 64 random alpha numerical characters, but can be anything",
      "clientName"      -> SimpleStringType ~~> "The name of the api key, for humans ;-)",
      "authorizedGroup" -> SimpleStringType ~~> "The group id on which the key is authorized",
      "enabled"         -> SimpleBooleanType ~~> "Whether or not the key is enabled. If disabled, resources won't be available to calls using this key",
      "throttlingQuota" -> SimpleLongType ~~> "Authorized number of calls per second, measured on 10 seconds",
      "dailyQuota"      -> SimpleLongType ~~> "Authorized number of calls per day",
      "monthlyQuota"    -> SimpleLongType ~~> "Authorized number of calls per month",
      "metadata"        -> SimpleObjectType ~~> "Bunch of metadata for the key"
    )
  )

  def Group = Json.obj(
    "description" -> "An Otoroshi service group is just a group of service descriptor. It is useful to be able to define Api Keys for the whole group",
    "type"        -> "object",
    "required"    -> Json.arr("id", "name"),
    "properties" -> Json.obj(
      "id"          -> SimpleStringType ~~> "The unique id of the group. Usually 64 random alpha numerical characters, but can be anything",
      "name"        -> SimpleStringType ~~> "The name of the group",
      "description" -> SimpleStringType ~~> "The descriptoin of the group"
    )
  )

  def Auth0Config = Json.obj(
    "description" -> "Configuration for Auth0 domain",
    "type"        -> "object",
    "required"    -> Json.arr("clientId", "clientSecret", "domain", "callbackUrl"),
    "properties" -> Json.obj(
      "clientId"     -> SimpleStringType ~~> "Auth0 client id",
      "clientSecret" -> SimpleStringType ~~> "Auth0 client secret",
      "domain"       -> SimpleStringType ~~> "Auth0 domain",
      "callbackUrl"  -> SimpleStringType ~~> "Auth0 callback URL"
    )
  )

  def MailgunSettings = Json.obj(
    "description" -> "Configuration for mailgun api client",
    "type"        -> "object",
    "required"    -> Json.arr("apiKey", "domain"),
    "properties" -> Json.obj(
      "apiKey" -> SimpleStringType ~~> "Mailgun Api Key",
      "domain" -> SimpleStringType ~~> "Mailgun domain"
    )
  )

  def CleverSettings = Json.obj(
    "description" -> "Configuration for CleverCloud client",
    "type"        -> "object",
    "required"    -> Json.arr("consumerKey", "consumerSecret", "token", "secret", "orgaId"),
    "properties" -> Json.obj(
      "consumerKey"    -> SimpleStringType ~~> "CleverCloud consumer key",
      "consumerSecret" -> SimpleStringType ~~> "CleverCloud consumer token",
      "token"          -> SimpleStringType ~~> "CleverCloud oauth token",
      "secret"         -> SimpleStringType ~~> "CleverCloud oauth secret",
      "orgaId"         -> SimpleStringType ~~> "CleverCloud organization id"
    )
  )

  def GlobalConfig = Json.obj(
    "type" -> "object",
    "required" -> Json.arr(
      "streamEntityOnly",
      "autoLinkToDefaultGroup",
      "limitConcurrentRequests",
      "maxConcurrentRequests",
      "useCircuitBreakers",
      "apiReadOnly",
      "u2fLoginOnly",
      "ipFiltering",
      "throttlingQuota",
      "perIpThrottlingQuota",
      "analyticsEventsUrl",
      "analyticsWebhooks",
      "alertsWebhooks",
      "alertsEmails",
      "endlessIpAddresses"
    ),
    "description" -> "The global config object of Otoroshi, used to customize settings of the current Otoroshi instance",
    "properties" -> Json.obj(
      "lines"                   -> ArrayOf(SimpleStringType) ~~> "Possibles lines for Otoroshi",
      "streamEntityOnly"        -> SimpleBooleanType ~~> "HTTP will be streamed only. Doesn't work with old browsers",
      "autoLinkToDefaultGroup"  -> SimpleBooleanType ~~> "If not defined, every new service descriptor will be added to the default group",
      "limitConcurrentRequests" -> SimpleBooleanType ~~> "If enabled, Otoroshi will reject new request if too much at the same time",
      "maxConcurrentRequests"   -> SimpleLongType ~~> "The number of authorized request processed at the same time",
      "maxHttp10ResponseSize"   -> SimpleLongType ~~> "The max size in bytes of an HTTP 1.0 response",
      "useCircuitBreakers"      -> SimpleBooleanType ~~> "If enabled, services will be authorized to use circuit breakers",
      "apiReadOnly"             -> SimpleBooleanType ~~> "If enabled, Admin API won't be able to write/update/delete entities",
      "u2fLoginOnly"            -> SimpleBooleanType ~~> "If enabled, login to backoffice through Auth0 will be disabled",
      "ipFiltering"             -> Ref("IpFiltering"),
      "throttlingQuota"         -> SimpleLongType ~~> "Authorized number of calls per second globally, measured on 10 seconds",
      "perIpThrottlingQuota"    -> SimpleLongType ~~> "Authorized number of calls per second globally per IP address, measured on 10 seconds",
      "analyticsEventsUrl"      -> SimpleUriType ~~> "The URL to get analytics events from",
      "analyticsWebhooks"       -> ArrayOf(Ref("Webhook")) ~~> "Webhook that will receive all internal Otoroshi events",
      "alertsWebhooks"          -> ArrayOf(Ref("Webhook")) ~~> "Webhook that will receive all Otoroshi alert events",
      "alertsEmails"            -> ArrayOf(SimpleEmailType) ~~> "Email addresses that will receive all Otoroshi alert events",
      "endlessIpAddresses"      -> ArrayOf(SimpleIpv4Type) ~~> "IP addresses for which any request to Otoroshi will respond with 128 Gb of zeros",
      "middleFingers"           -> SimpleBooleanType ~~> "Use middle finger emoji as a response character for endless HTTP responses",
      "maxLogsSize"             -> SimpleIntType ~~> "Number of events kept locally",
      "cleverSettings"          -> Ref("CleverSettings") ~~> "Optional CleverCloud configuration",
      "mailGunSettings"         -> Ref("MailgunSettings") ~~> "Optional mailgun configuration",
      "backofficeAuth0Config"   -> Ref("Auth0Config") ~~> "Optional configuration for the backoffice Auth0 domain",
      "privateAppsAuth0Config"  -> Ref("Auth0Config") ~~> "Optional configuration for the private apps Auth0 domain"
    )
  )

  def Webhook = Json.obj(
    "description" -> "A callback URL where events are posted",
    "type"        -> "object",
    "required"    -> Json.arr("url", "headers"),
    "properties" -> Json.obj(
      "url"     -> SimpleUriType ~~> "The URL where events are posted",
      "headers" -> SimpleObjectType ~~> "Headers to authorize the call or whatever"
    )
  )

  def ImportExportStats = Json.obj(
    "description" -> "Global stats for the current Otoroshi instances",
    "type"        -> "object",
    "required"    -> Json.arr("calls", "dataIn", "dataOut"),
    "properties" -> Json.obj(
      "calls"   -> SimpleLongType ~~> "Number of calls to Otoroshi globally",
      "dataIn"  -> SimpleLongType ~~> "The amount of data sent to Otoroshi globally",
      "dataOut" -> SimpleLongType ~~> "The amount of data sent from Otoroshi globally"
    )
  )

  def U2FAdmin = Json.obj(
    "description" -> "Administrator using FIDO U2F device to access Otoroshi",
    "type"        -> "object",
    "required"    -> Json.arr("username", "label", "password", "createdAt", "registration"),
    "properties" -> Json.obj(
      "username"     -> SimpleStringType ~~> "The email address of the user",
      "label"        -> SimpleStringType ~~> "The label for the user",
      "password"     -> SimpleStringType ~~> "The hashed password of the user",
      "createdAt"    -> SimpleLongType ~~> "The creation date of the user",
      "registration" -> SimpleObjectType ~~> "The U2F registration slug"
    )
  )

  def SimpleAdmin = Json.obj(
    "description" -> "Administrator using just login/password tuple to access Otoroshi",
    "type"        -> "object",
    "required"    -> Json.arr("username", "label", "password", "createdAt"),
    "properties" -> Json.obj(
      "username"  -> SimpleStringType ~~> "The email address of the user",
      "label"     -> SimpleStringType ~~> "The label for the user",
      "password"  -> SimpleStringType ~~> "The hashed password of the user",
      "createdAt" -> SimpleLongType ~~> "The creation date of the user"
    )
  )

  def ErrorTemplate = Json.obj(
    "description" -> "Error templates for a service descriptor",
    "type"        -> "object",
    "required" -> Json.arr("serviceId",
                           "template40x",
                           "template50x",
                           "templateBuild",
                           "templateMaintenance",
                           "messages"),
    "properties" -> Json.obj(
      "serviceId"           -> SimpleStringType ~~> "The Id of the service for which the error template is enabled",
      "template40x"         -> SimpleStringType ~~> "The html template for 40x errors",
      "template50x"         -> SimpleStringType ~~> "The html template for 50x errors",
      "templateBuild"       -> SimpleStringType ~~> "The html template for build page",
      "templateMaintenance" -> SimpleStringType ~~> "The html template for maintenance page",
      "messages"            -> SimpleObjectType ~~> "Map for custom messages"
    )
  )

  def ImportExport = Json.obj(
    "description" -> "The structure that can be imported to or exported from Otoroshi. It represent the memory state of Otoroshi",
    "type"        -> "object",
    "required" -> Json.arr("label",
                           "dateRaw",
                           "date",
                           "stats",
                           "config",
                           "admins",
                           "simpleAdmins",
                           "serviceGroups",
                           "apiKeys",
                           "serviceDescriptors",
                           "errorTemplates"),
    "properties" -> Json.obj(
      "label"              -> SimpleStringType,
      "dateRaw"            -> SimpleLongType,
      "date"               -> SimpleDateTimeType,
      "stats"              -> Ref("ImportExportStats") ~~> "Current global stats at the time of export",
      "config"             -> Ref("GlobalConfig") ~~> "Current global config at the time of export",
      "appConfig"          -> SimpleObjectType ~~> "Current env variables at the time of export",
      "admins"             -> ArrayOf(U2FAdmin) ~~> "Current U2F admin at the time of export",
      "simpleAdmins"       -> ArrayOf(SimpleAdmin) ~~> "Current simple admins at the time of export",
      "serviceGroups"      -> ArrayOf(Group) ~~> "Current service groups at the time of export",
      "apiKeys"            -> ArrayOf(ApiKey) ~~> "Current apik keys at the time of export",
      "serviceDescriptors" -> ArrayOf(Service) ~~> "Current service descriptors at the time of export",
      "errorTemplates"     -> ArrayOf(ErrorTemplate) ~~> "Current error templates at the time of export"
    )
  )

  def Stats = Json.obj(
    "description" -> "Live stats for a service or globally",
    "type"        -> "object",
    "required" -> Json.arr("calls",
                           "dataIn",
                           "dataOut",
                           "rate",
                           "duration",
                           "overhead",
                           "dataInRate",
                           "dataOutRate",
                           "concurrentHandledRequests"),
    "properties" -> Json.obj(
      "calls"                     -> SimpleLongType ~~> "Number of calls on the specified service or globally",
      "dataIn"                    -> SimpleLongType ~~> "The amount of data sent to the specified service or Otoroshi globally",
      "dataOut"                   -> SimpleLongType ~~> "The amount of data sent from the specified service or Otoroshi globally",
      "rate"                      -> SimpleDoubleType ~~> "The rate of data sent from and to the specified service or Otoroshi globally",
      "duration"                  -> SimpleDoubleType ~~> "The average duration for a call",
      "overhead"                  -> SimpleDoubleType ~~> "The average overhead time induced by Otoroshi for each call",
      "dataInRate"                -> SimpleDoubleType ~~> "The rate of data sent to the specified service or Otoroshi globally",
      "dataOutRate"               -> SimpleDoubleType ~~> "The rate of data sent from the specified service or Otoroshi globally",
      "concurrentHandledRequests" -> SimpleLongType ~~> "The number of concurrent request currently"
    )
  )

  def Patch = Json.obj(
    "description" -> "A set of changes described in JSON Patch format: http://jsonpatch.com/ (RFC 6902)",
    "type"        -> "array",
    "items" -> Json.obj(
      "type"     -> "object",
      "required" -> Json.arr("op", "path"),
      "properties" -> Json.obj(
        "op" -> Json.obj(
          "type" -> "string",
          "enum" -> Json.arr("add", "replace", "remove", "copy", "test")
        ),
        "path" -> SimpleStringType,
        "value" -> Json.obj(
          "schemas" -> Json.obj(
            "AnyValue" -> Json.obj(
              "nullable"    -> true,
              "description" -> "Can be any value - string, number, boolean, array or object."
            )
          ),
          "required" -> false
        )
      )
    )
  )

  def Quotas = Json.obj(
    "description" -> "Quotas state for an api key on a service group",
    "type"        -> "object",
    "required" -> Json.arr(
      "authorizedCallsPerSec",
      "currentCallsPerSec",
      "remainingCallsPerSec",
      "authorizedCallsPerDay",
      "currentCallsPerDay",
      "remainingCallsPerDay",
      "authorizedCallsPerMonth",
      "currentCallsPerMonth",
      "remainingCallsPerMonth"
    ),
    "properties" -> Json.obj(
      "authorizedCallsPerSec"   -> SimpleLongType ~~> "The number of authorized calls per second",
      "currentCallsPerSec"      -> SimpleLongType ~~> "The current number of calls per second",
      "remainingCallsPerSec"    -> SimpleLongType ~~> "The remaining number of calls per second",
      "authorizedCallsPerDay"   -> SimpleLongType ~~> "The number of authorized calls per day",
      "currentCallsPerDay"      -> SimpleLongType ~~> "The current number of calls per day",
      "remainingCallsPerDay"    -> SimpleLongType ~~> "The remaining number of calls per day",
      "authorizedCallsPerMonth" -> SimpleLongType ~~> "The number of authorized calls per month",
      "currentCallsPerMonth"    -> SimpleLongType ~~> "The current number of calls per month",
      "remainingCallsPerMonth"  -> SimpleLongType ~~> "The number of authorized calls per month"
    )
  )

  def Deleted = Json.obj(
    "type"     -> "object",
    "required" -> Json.arr("deleted"),
    "properties" -> Json.obj(
      "deleted" -> SimpleBooleanType
    )
  )

  def Done = Json.obj(
    "type"     -> "object",
    "required" -> Json.arr("done"),
    "properties" -> Json.obj(
      "done" -> SimpleBooleanType
    )
  )

  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  // Operation definitions

  def NewApiKey: JsValue = Json.obj(
    "get" -> Operation(
      tag = "templates",
      summary = "Get a template of an Otoroshi Api Key",
      description = "Get a template of an Otoroshi Api Key. The generated entity is not persisted",
      operationId = "initiateApiKey",
      goodResponse = GoodResponse(Ref("ApiKey"))
    )
  )

  def NewService: JsValue = Json.obj(
    "get" -> Operation(
      tag = "templates",
      summary = "Get a template of an Otoroshi service descriptor",
      description = "Get a template of an Otoroshi service descriptor. The generated entity is not persisted",
      operationId = "initiateService",
      goodResponse = GoodResponse(Ref("Service"))
    )
  )

  def NewGroup: JsValue = Json.obj(
    "get" -> Operation(
      tag = "templates",
      summary = "Get a template of an Otoroshi service group",
      description = "Get a template of an Otoroshi service group. The generated entity is not persisted",
      operationId = "initiateServiceGroup",
      goodResponse = GoodResponse(Ref("Group"))
    )
  )

  def AllLines(): JsValue = Json.obj(
    "get" -> Operation(
      tag = "environments",
      summary = "Get all environments",
      description = "Get all environments provided by the current Otoroshi instance",
      operationId = "allLines",
      goodResponse = GoodResponse(Ref("Environment"))
    )
  )

  def ServicesForLine: JsValue = Json.obj(
    "get" -> Operation(
      tag = "environments",
      summary = "Get all services for an environment",
      description = "Get all services for an environment provided by the current Otoroshi instance",
      operationId = "servicesForALine",
      parameters = Json.arr(
        PathParam("line", "The environment where to find services")
      ),
      goodResponse = GoodResponse(ArrayOf(Ref("Service")))
    )
  )

  def QuotasOfTheApiKeyOfAService = Json.obj(
    "get" -> Operation(
      tag = "apikeys",
      summary = "Get the quota state of an api key",
      description = "Get the quota state of an api key",
      operationId = "apiKeyQuotas",
      parameters = Json.arr(
        PathParam("serviceId", "The api key service id"),
        PathParam("clientId", "the api key id")
      ),
      goodResponse = GoodResponse(Ref("Quotas"))
    )
  )

  def QuotasOfTheApiKeyOfAGroup = Json.obj(
    "get" -> Operation(
      tag = "apikeys",
      summary = "Get the quota state of an api key",
      description = "Get the quota state of an api key",
      operationId = "apiKeyFromGroupQuotas",
      parameters = Json.arr(
        PathParam("groupId", "The api key group id"),
        PathParam("clientId", "the api key id")
      ),
      goodResponse = GoodResponse(Ref("Quotas"))
    )
  )

  def GroupForApiKey = Json.obj(
    "get" -> Operation(
      tag = "apikeys",
      summary = "Get the group of an api key",
      description = "Get the group of an api key",
      operationId = "apiKeyGroup",
      parameters = Json.arr(
        PathParam("serviceId", "The api key service id"),
        PathParam("clientId", "the api key id")
      ),
      goodResponse = GoodResponse(Ref("Group"))
    )
  )

  def ApiKeyManagementForService = Json.obj(
    "get" -> Operation(
      tag = "apikeys",
      summary = "Get an api key",
      description = "Get an api key for a specified service descriptor",
      operationId = "apiKey",
      parameters = Json.arr(
        PathParam("serviceId", "The api key service id"),
        PathParam("clientId", "the api key id")
      ),
      goodResponse = GoodResponse(Ref("ApiKey"))
    ),
    "put" -> Operation(
      tag = "apikeys",
      summary = "Update an api key",
      description = "Update an api key for a specified service descriptor",
      operationId = "updateApiKey",
      parameters = Json.arr(
        PathParam("serviceId", "The api key service id"),
        PathParam("clientId", "the api key id"),
        BodyParam("The updated api key", Ref("ApiKey"))
      ),
      goodResponse = GoodResponse(Ref("ApiKey"))
    ),
    "patch" -> Operation(
      tag = "apikeys",
      summary = "Update an api key with a diff",
      description = "Update an api key for a specified service descriptor with a diff",
      operationId = "patchApiKey",
      parameters = Json.arr(
        PathParam("serviceId", "The api key service id"),
        PathParam("clientId", "the api key id"),
        BodyParam("The patch for the api key", Ref("Patch"))
      ),
      goodResponse = GoodResponse(Ref("ApiKey"))
    ),
    "delete" -> Operation(
      tag = "apikeys",
      summary = "Delete an api key",
      description = "Delete an api key for a specified service descriptor",
      operationId = "deleteApiKey",
      parameters = Json.arr(
        PathParam("serviceId", "The api key service id"),
        PathParam("clientId", "the api key id")
      ),
      goodResponse = GoodResponse(Ref("Deleted"))
    )
  )

  def ApiKeyManagementForGroup = Json.obj(
    "get" -> Operation(
      tag = "apikeys",
      summary = "Get an api key",
      description = "Get an api key for a specified service group",
      operationId = "apiKeyFromGroup",
      parameters = Json.arr(
        PathParam("groupId", "The api key group id"),
        PathParam("clientId", "the api key id")
      ),
      goodResponse = GoodResponse(Ref("ApiKey"))
    ),
    "put" -> Operation(
      tag = "apikeys",
      summary = "Update an api key",
      description = "Update an api key for a specified service group",
      operationId = "updateApiKeyFromGroup",
      parameters = Json.arr(
        PathParam("groupId", "The api key group id"),
        PathParam("clientId", "the api key id"),
        BodyParam("The updated api key", Ref("ApiKey"))
      ),
      goodResponse = GoodResponse(Ref("ApiKey"))
    ),
    "patch" -> Operation(
      tag = "apikeys",
      summary = "Update an api key with a diff",
      description = "Update an api key for a specified service descriptor with a diff",
      operationId = "patchApiKeyFromGroup",
      parameters = Json.arr(
        PathParam("groupId", "The api key group id"),
        PathParam("clientId", "the api key id"),
        BodyParam("The patch for the api key", Ref("Patch"))
      ),
      goodResponse = GoodResponse(Ref("ApiKey"))
    ),
    "delete" -> Operation(
      tag = "apikeys",
      summary = "Delete an api key",
      description = "Delete an api key for a specified service group",
      operationId = "deleteApiKeyFromGroup",
      parameters = Json.arr(
        PathParam("groupId", "The api key group id"),
        PathParam("clientId", "the api key id")
      ),
      goodResponse = GoodResponse(Ref("Deleted"))
    )
  )

  def ApiKeysManagementForService = Json.obj(
    "get" -> Operation(
      tag = "apikeys",
      summary = "Get all api keys for the group of a service",
      description = "Get all api keys for the group of a service",
      operationId = "apiKeys",
      parameters = Json.arr(
        PathParam("serviceId", "The api key service id")
      ),
      goodResponse = GoodResponse(ArrayOf(Ref("ApiKey")))
    ),
    "post" -> Operation(
      tag = "apikeys",
      summary = "Create a new api key for a service",
      description = "Create a new api key for a service",
      operationId = "createApiKey",
      parameters = Json.arr(
        PathParam("serviceId", "The api key service id"),
        BodyParam("The api key to create", Ref("ApiKey"))
      ),
      goodResponse = GoodResponse(Ref("ApiKey"))
    )
  )

  def ApiKeysManagementForGroup = Json.obj(
    "get" -> Operation(
      tag = "apikeys",
      summary = "Get all api keys for the group of a service",
      description = "Get all api keys for the group of a service",
      operationId = "apiKeysFromGroup",
      parameters = Json.arr(
        PathParam("groupId", "The api key group id")
      ),
      goodResponse = GoodResponse(ArrayOf(Ref("ApiKey")))
    ),
    "post" -> Operation(
      tag = "apikeys",
      summary = "Create a new api key for a group",
      description = "Create a new api key for a group",
      operationId = "createApiKeyFromGroup",
      parameters = Json.arr(
        PathParam("groupId", "The api key group id"),
        BodyParam("The api key to create", Ref("ApiKey"))
      ),
      goodResponse = GoodResponse(Ref("ApiKey"))
    )
  )

  def ApiKeys = Json.obj(
    "get" -> Operation(
      tag = "apikeys",
      summary = "Get all api keys",
      description = "Get all api keys",
      operationId = "allApiKeys",
      goodResponse = GoodResponse(ArrayOf(Ref("ApiKey")))
    )
  )

  def GroupManagement = Json.obj(
    "get" -> Operation(
      tag = "groups",
      summary = "Get a service group",
      description = "Get a service group",
      operationId = "serviceGroup",
      parameters = Json.arr(
        PathParam("serviceGroupId", "The service group id")
      ),
      goodResponse = GoodResponse(Ref("Group"))
    ),
    "put" -> Operation(
      tag = "groups",
      summary = "Update a service group",
      description = "Update a service group",
      operationId = "updateGroup",
      parameters = Json.arr(
        PathParam("serviceGroupId", "The service group id"),
        BodyParam("The updated service group", Ref("Group"))
      ),
      goodResponse = GoodResponse(Ref("Group"))
    ),
    "patch" -> Operation(
      tag = "groups",
      summary = "Update a service group with a diff",
      description = "Update a service group with a diff",
      operationId = "patchGroup",
      parameters = Json.arr(
        PathParam("serviceGroupId", "The service group id"),
        BodyParam("The patch for the service group", Ref("Patch"))
      ),
      goodResponse = GoodResponse(Ref("Group"))
    ),
    "delete" -> Operation(
      tag = "groups",
      summary = "Delete a service group",
      description = "Delete a service group",
      operationId = "deleteGroup",
      parameters = Json.arr(
        PathParam("serviceGroupId", "The service group id")
      ),
      goodResponse = GoodResponse(Ref("Deleted"))
    )
  )

  def GroupsManagement = Json.obj(
    "get" -> Operation(
      tag = "groups",
      summary = "Get all service groups",
      description = "Get all service groups",
      operationId = "allServiceGroups",
      goodResponse = GoodResponse(ArrayOf(Ref("Group")))
    ),
    "post" -> Operation(
      tag = "groups",
      summary = "Create a new service group",
      description = "Create a new service group",
      operationId = "createGroup",
      parameters = Json.arr(
        BodyParam("The service group to create", Ref("Group"))
      ),
      goodResponse = GoodResponse(Ref("Group"))
    )
  )

  def ServicesManagement = Json.obj(
    "get" -> Operation(
      tag = "services",
      summary = "Get all services",
      description = "Get all services",
      operationId = "allServices",
      goodResponse = GoodResponse(ArrayOf(Ref("Service")))
    ),
    "post" -> Operation(
      tag = "services",
      summary = "Create a new service descriptor",
      description = "Create a new service descriptor",
      operationId = "createService",
      parameters = Json.arr(
        BodyParam("The service descriptor to create", Ref("Service"))
      ),
      goodResponse = GoodResponse(Ref("Service"))
    )
  )

  def ServicesForGroup = Json.obj(
    "get" -> Operation(
      tag = "services",
      summary = "Get all services descriptor for a group",
      description = "Get all services descriptor for a group",
      operationId = "serviceGroupServices",
      parameters = Json.arr(
        PathParam("serviceGroupId", "The service group id")
      ),
      goodResponse = GoodResponse(ArrayOf(Ref("ApiKey")))
    )
  )

  def ServiceManagement = Json.obj(
    "get" -> Operation(
      tag = "services",
      summary = "Get a service descriptor",
      description = "Get a service descriptor",
      operationId = "service",
      parameters = Json.arr(
        PathParam("serviceId", "The service id")
      ),
      goodResponse = GoodResponse(Ref("Service"))
    ),
    "put" -> Operation(
      tag = "services",
      summary = "Update a service descriptor",
      description = "Update a service descriptor",
      operationId = "updateService",
      parameters = Json.arr(
        PathParam("serviceId", "The service id"),
        BodyParam("The updated service descriptor", Ref("Service"))
      ),
      goodResponse = GoodResponse(Ref("Service"))
    ),
    "patch" -> Operation(
      tag = "services",
      summary = "Update a service descriptor with a diff",
      description = "Update a service descriptor with a diff",
      operationId = "patchService",
      parameters = Json.arr(
        PathParam("serviceId", "The service id"),
        BodyParam("The patch for the service", Ref("Patch"))
      ),
      goodResponse = GoodResponse(Ref("Service"))
    ),
    "delete" -> Operation(
      tag = "services",
      summary = "Delete a service descriptor",
      description = "Delete a service descriptor",
      operationId = "deleteService",
      parameters = Json.arr(
        PathParam("serviceId", "The service id")
      ),
      goodResponse = GoodResponse(Ref("Deleted"))
    )
  )

  def ImportExportJson = Json.obj(
    "get" -> Operation(
      tag = "import",
      summary = "Export the full state of Otoroshi",
      description = "Export the full state of Otoroshi",
      operationId = "fullExport",
      goodResponse = GoodResponse(Ref("ImportExport"))
    ),
    "post" -> Operation(
      tag = "import",
      summary = "Import the full state of Otoroshi",
      description = "Import the full state of Otoroshi",
      operationId = "fullImport",
      parameters = Json.arr(
        BodyParam("The full export", Ref("ImportExport"))
      ),
      goodResponse = GoodResponse(Ref("Done"))
    )
  )

  def GlobalConfigManagement = Json.obj(
    "get" -> Operation(
      tag = "configuration",
      summary = "Get the full configuration of Otoroshi",
      description = "Get the full configuration of Otoroshi",
      operationId = "globalConfig",
      goodResponse = GoodResponse(Ref("GlobalConfig"))
    ),
    "put" -> Operation(
      tag = "configuration",
      summary = "Update the global configuration",
      description = "Update the global configuration",
      operationId = "patchGlobalConfig",
      parameters = Json.arr(
        BodyParam("The updated global config", Ref("GlobalConfig"))
      ),
      goodResponse = GoodResponse(Ref("GlobalConfig"))
    ),
    "patch" -> Operation(
      tag = "configuration",
      summary = "Update the global configuration with a diff",
      description = "Update the global configuration with a diff",
      operationId = "patchGlobalConfig",
      parameters = Json.arr(
        BodyParam("The updated global config as patch", Ref("Patch"))
      ),
      goodResponse = GoodResponse(Ref("GlobalConfig"))
    )
  )

  def ImportFromFile = Json.obj(
    "post" -> Operation(
      tag = "import",
      summary = "Import the full state of Otoroshi as a file",
      description = "Import the full state of Otoroshi as a file",
      operationId = "fullImportFromFile",
      parameters = Json.arr(
        BodyParam("The full export", Ref("ImportExport"))
      ),
      goodResponse = GoodResponse(Ref("Done"))
    )
  )

  def ServiceTargetsManagement = Json.obj(
    "get" -> Operation(
      tag = "services",
      summary = "Get a service descriptor targets",
      description = "Get a service descriptor targets",
      operationId = "serviceTargets",
      parameters = Json.arr(
        PathParam("serviceId", "The service id")
      ),
      goodResponse = GoodResponse(ArrayOf(Ref("Target")))
    ),
    "post" -> Operation(
      tag = "services",
      summary = "Add a target to a service descriptor",
      description = "Add a target to a service descriptor",
      operationId = "serviceAddTarget",
      parameters = Json.arr(
        PathParam("serviceId", "The service id"),
        BodyParam("The updated service descriptor", Ref("Target"))
      ),
      goodResponse = GoodResponse(ArrayOf(Ref("Target")))
    ),
    "patch" -> Operation(
      tag = "services",
      summary = "Update a service descriptor targets",
      description = "Update a service descriptor targets",
      operationId = "updateServiceTargets",
      parameters = Json.arr(
        PathParam("serviceId", "The service id"),
        BodyParam("The patch for the service targets", Ref("Patch"))
      ),
      goodResponse = GoodResponse(ArrayOf(Ref("Target")))
    ),
    "delete" -> Operation(
      tag = "services",
      summary = "Delete a service descriptor target",
      description = "Delete a service descriptor target",
      operationId = "serviceDeleteTarget",
      parameters = Json.arr(
        PathParam("serviceId", "The service id")
      ),
      goodResponse = GoodResponse(ArrayOf(Ref("Target")))
    )
  )

  def ServiceTemplatesManagement = Json.obj(
    "get" -> Operation(
      tag = "services",
      summary = "Get a service descriptor error template",
      description = "Get a service descriptor error template",
      operationId = "serviceTemplate",
      parameters = Json.arr(
        PathParam("serviceId", "The service id")
      ),
      goodResponse = GoodResponse(Ref("ErrorTemplate"))
    ),
    "put" -> Operation(
      tag = "services",
      summary = "Update an error template to a service descriptor",
      description = "Update an error template to a service descriptor",
      operationId = "updateServiceTemplate",
      parameters = Json.arr(
        PathParam("serviceId", "The service id"),
        BodyParam("The updated service descriptor template", Ref("ErrorTemplate"))
      ),
      goodResponse = GoodResponse(Ref("ErrorTemplate"))
    ),
    "post" -> Operation(
      tag = "services",
      summary = "Create a service descriptor error template",
      description = "Update a service descriptor targets",
      operationId = "createServiceTemplate",
      parameters = Json.arr(
        PathParam("serviceId", "The service id"),
        BodyParam("The patch for the service error template", Ref("ErrorTemplate"))
      ),
      goodResponse = GoodResponse(Ref("ErrorTemplate"))
    ),
    "delete" -> Operation(
      tag = "services",
      summary = "Delete a service descriptor error template",
      description = "Delete a service descriptor error template",
      operationId = "deleteServiceTemplate",
      parameters = Json.arr(
        PathParam("serviceId", "The service id")
      ),
      goodResponse = GoodResponse(Ref("Deleted"))
    )
  )

  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  // Swagger definition

  def swaggerDescriptor(): JsValue = {
    Json.obj(
      "swagger" -> "2.0",
      "info" -> Json.obj(
        "version"     -> "1.0.0",
        "title"       -> "Otoroshi Admin API",
        "description" -> "Admin API of the Otoroshi reverse proxy",
        "contact" -> Json.obj(
          "name"  -> "Otoroshi Team",
          "email" -> "oss@maif.fr"
        ),
        "license" -> Json.obj(
          "name" -> "Apache 2.0",
          "url"  -> "http://www.apache.org/licenses/LICENSE-2.0.html"
        )
      ),
      "tags" -> Json.arr(
        Tag("configuration", "Everything about Otoroshi global configuration"),
        Tag("import", "Everything about Otoroshi import/export"),
        Tag("templates", "Everything about Otoroshi entities templates"),
        Tag("environments", "Everything about Otoroshi Environments"),
        Tag("groups", "Everything about Otoroshi service groups"),
        Tag("apikeys", "Everything about Otoroshi api keys"),
        Tag("services", "Everything about Otoroshi service descriptors"),
        Tag("stats", "Everything about Otoroshi stats")
      ),
      "externalDocs" -> Json.obj(
        "description" -> "Find out more about Otoroshi",
        "url"         -> "https://maif.github.io/otoroshi/"
      ),
      "host"     -> env.adminApiExposedHost,
      "basePath" -> "/api",
      "schemes"  -> Json.arr(env.exposedRootScheme),
      "paths" -> Json.obj(
        "/new/apikey"                                         -> NewApiKey,
        "/new/service"                                        -> NewService,
        "/new/group"                                          -> NewGroup,
        "/lines"                                              -> AllLines,
        "/lines/{line}/services"                              -> ServicesForLine,
        "/api/services/{serviceId}/apikeys/{clientId}/quotas" -> QuotasOfTheApiKeyOfAService,
        "/api/services/{serviceId}/apikeys/{clientId}/group"  -> GroupForApiKey,
        "/api/services/{serviceId}/apikeys/{clientId}"        -> ApiKeyManagementForService,
        "/api/services/{serviceId}/apikeys"                   -> ApiKeysManagementForService,
        "/api/groups/{groupId}/apikeys/{clientId}/quotas"     -> QuotasOfTheApiKeyOfAGroup,
        "/api/groups/{groupId}/apikeys/{clientId}"            -> ApiKeyManagementForGroup,
        "/api/groups/{groupId}/apikeys"                       -> ApiKeysManagementForGroup,
        "/api/apikeys"                                        -> ApiKeys,
        "/api/services/{serviceId}/template"                  -> ServiceTemplatesManagement,
        "/api/services/{serviceId}/targets"                   -> ServiceTargetsManagement,
        "/api/services/{serviceId}"                           -> ServiceManagement,
        "/api/services"                                       -> ServicesManagement,
        "/api/groups/{serviceGroupId}/services"               -> ServicesForGroup,
        "/api/groups/{serviceGroupId}"                        -> GroupManagement,
        "/api/groups"                                         -> GroupsManagement,
        "/api/live/{id}" -> Json.obj(
          "get" -> Operation(
            tag = "stats",
            summary = "Get live feed of otoroshi stats",
            description = "Get live feed of global otoroshi stats (global) or for a service {id}",
            operationId = "serviceLiveStats",
            parameters = Json.arr(
              PathParam("id", "The service id or global for otoroshi stats")
            ),
            goodResponse = Json.obj(
              "description" -> "Successful operation",
              "contentType" -> "text/event-stream",
              "schema"      -> Ref("Stats")
            )
          )
        ),
        "/api/live" -> Json.obj(
          "get" -> Operation(
            tag = "stats",
            summary = "Get global otoroshi stats",
            description = "Get global otoroshi stats",
            operationId = "globalLiveStats",
            goodResponse = GoodResponse(Ref("Stats"))
          )
        ),
        "/api/globalconfig"  -> GlobalConfigManagement,
        "/api/otoroshi.json" -> ImportExportJson,
        "/api/import"        -> ImportFromFile
      ),
      "securityDefinitions" -> Json.obj(
        "otoroshi_auth" -> Json.obj(
          "type" -> "basic"
        )
      ),
      "definitions" -> Json.obj(
        "ApiKey"         -> ApiKey,
        "Auth0Config"    -> Auth0Config,
        "Canary"         -> Canary,
        "CleverSettings" -> CleverSettings,
        "ClientConfig"   -> ClientConfig,
        "Deleted"        -> Deleted,
        "Done"           -> Done,
        "Environment" -> Json.obj(
          "type"        -> "string",
          "example"     -> "prod",
          "description" -> "The name of the environment for service descriptors"
        ),
        "ErrorTemplate"     -> ErrorTemplate,
        "ExposedApi"        -> ExposedApi,
        "GlobalConfig"      -> GlobalConfig,
        "Group"             -> Group,
        "HealthCheck"       -> HealthCheck,
        "ImportExport"      -> ImportExport,
        "ImportExportStats" -> ImportExportStats,
        "IpFiltering"       -> IpFiltering,
        "MailgunSettings"   -> MailgunSettings,
        "Patch"             -> Patch,
        "Quotas"            -> Quotas,
        "Service"           -> Service,
        "SimpleAdmin"       -> SimpleAdmin,
        "Stats"             -> Stats,
        "StatsdConfig"      -> StatsdConfig,
        "Target"            -> Target,
        "U2FAdmin"          -> U2FAdmin,
        "Webhook"           -> Webhook
      )
    )
  }
}
