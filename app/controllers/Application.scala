package controllers

import jodd.lagarto.dom.{LagartoDOMBuilder, NodeSelector, Document}
import play.api.mvc._
import util.{AmazonPageParser, HttpClient}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConversions._

object Application extends Controller {

  def index = Action.async {
    AmazonPageParser.parse("B001E4KFG0") map(item => Ok(views.html.rating(item)))
  }
}