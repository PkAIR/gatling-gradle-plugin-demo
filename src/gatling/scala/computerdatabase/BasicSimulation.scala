package computerdatabase

import io.gatling.core.Predef._
import io.gatling.core.feeder.BatchableFeederBuilder
import io.gatling.core.structure.{ChainBuilder, ScenarioBuilder}
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder

class BasicSimulation extends Simulation {
  val httpProtocol: HttpProtocolBuilder = http
    .baseUrl("https://computer-database.gatling.io") // Here is the root for all relative URLs
    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8") // Here are the common headers
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0")

  object SearchObject {
    val searchFeeder: BatchableFeederBuilder[String]#F = csv("testdata/search_data.csv").random
    val search: ChainBuilder = exec(http("Load home page")
      .get("/computers"))
      .pause(2)
      .feed(searchFeeder)
      .exec(http("Search computer by ${searchCriterion}")
        .get("/computers?f=${searchCriterion}")
        .check(css(selector = "a:contains('${searchCriterion}')", nodeAttribute = "href").saveAs(key = "computerUrl")))
      .pause(2)
      .exec(http("Select computer by url ${computerUrl}")
        .get("${computerUrl}"))
  }

  object BrowserObject {
    val browse: ChainBuilder = repeat(5, counterName = "index") {
      exec(http("Browse page ${index}")
        .get("/computers?p=${index}"))
        .pause(2)
    }
  }

  object CreateObject {
    val computersFeeder: BatchableFeederBuilder[String]#F = csv("testdata/computers.csv").circular
    val create: ChainBuilder = exec(http("Load create computer page")
      .get("/computers/new"))
      .pause(2)
      .feed(computersFeeder)
      .exec(http("Create computer with name ${computerName}")
        .post("/computers")
        .formParam("""name""", "${computerName}") // Note the triple double quotes: used in Scala for protecting a whole chain of characters (no need for backslash)
        .formParam("""introduced""", "${introduced}")
        .formParam("""discontinued""", "${discontinued}")
        .formParam("""company""", "${companyId}")
      .check(status.is(200)))
  }

  val admins: ScenarioBuilder = scenario("Admins").exec(SearchObject.search, BrowserObject.browse, CreateObject.create)
  val users: ScenarioBuilder = scenario("Regular users").exec(SearchObject.search, BrowserObject.browse)

  setUp(admins.inject(atOnceUsers(1)),
    users.inject(
      nothingFor(5),
      atOnceUsers(users = 1),
      //rampUsers(users = 5) during(10),
      constantUsersPerSec(rate = 5) during(20)))
    .protocols(httpProtocol)
}
