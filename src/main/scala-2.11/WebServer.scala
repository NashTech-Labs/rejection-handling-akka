import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.RejectionHandler
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model.{HttpEntity, StatusCodes, _}
import akka.http.scaladsl.server._
import StatusCodes._
import Directives._
import scala.io.StdIn

case class ErrorResponse(code: Int, `type`: String, message: String)
case class ProperResponse(code: Int, message: String)

object WebServer extends JsonHelper{

  def main(args: Array[String]) {
    implicit val system = ActorSystem("rejection-system")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    /*
    Using rejection handler to handle some of the predefined rejections.
     */
    implicit def rejectionHandler =
      RejectionHandler.newBuilder()
        .handle { case MissingQueryParamRejection(param) =>
          val errorResponse = write(ErrorResponse(BadRequest.intValue, "Missing Parameter", s"The required $param was not found."))
          complete(HttpResponse(BadRequest, entity = HttpEntity(ContentTypes.`application/json`, errorResponse)))
        }
        .handle { case AuthorizationFailedRejection =>
          val errorResponse = write(ErrorResponse(BadRequest.intValue, "Authorization", "The authorization check failed for you. Access Denied."))
          complete(HttpResponse(BadRequest, entity = HttpEntity(ContentTypes.`application/json`, errorResponse)))
        }
        .handleAll[MethodRejection] { methodRejections =>
        val names = methodRejections.map(_.supported.name)
        val errorResponse = write(ErrorResponse(MethodNotAllowed.intValue, "Not Allowed", s"Access to $names is not allowed."))
        complete(HttpResponse(MethodNotAllowed, entity = HttpEntity(ContentTypes.`application/json`, errorResponse)))
      }
        .handleNotFound {
          val errorResponse = write(ErrorResponse(NotFound.intValue, "NotFound", "The requested resource could not be found."))
          complete(HttpResponse(NotFound, entity = HttpEntity(ContentTypes.`application/json`, errorResponse)))
        }
        .result()

    /*
    Routes that do produce a rejection.
     */
    val route =
      path("hello") {
        get {
          val properResponse = write(ProperResponse(OK.intValue, "Hello!! How are you?"))
          complete(HttpResponse(OK, entity = HttpEntity(ContentTypes.`application/json`, properResponse)))
        }
      } ~ path("check") {
        parameters('color, 'bgColor) {
          (color, bgColor) =>
            val properResponse = write(ProperResponse(OK.intValue, s"Your preference is color $color with background color $bgColor."))
            complete(HttpResponse(OK, entity = HttpEntity(ContentTypes.`application/json`, properResponse)))
        }
      } ~ path("admin") {
        parameters('username, 'password) {
          (username, password) =>
            if (username.equals("knoldus") && password.equals("knoldus")) {
              val properResponse = write(ProperResponse(OK.intValue, "Welcome!!!"))
              complete(HttpResponse(OK, entity = HttpEntity(ContentTypes.`application/json`, properResponse)))
            } else {
              reject(AuthorizationFailedRejection)
            }
          }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
