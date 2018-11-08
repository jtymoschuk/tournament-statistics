package controllers

import java.nio.file.Files

import javax.inject.Inject
import org.joda.time.DateTime
import play.api.libs.json._
import play.api.mvc._

import scala.language.implicitConversions
import scala.util.control.NonFatal

class Application @Inject()(
                             battlefy: Battlefy,
                             eternalWarcry: EternalWarcry,
                             fs: FileSystem,
                             db: DB, graphics:
                             Graphics,
                             docs: Docs) extends Controller {

  def index = Action {
    Ok(views.html.index(battlefy.getCurrentTournament))
  }

  def validateDeck(url: String) = Action {
    try {
      val result = eternalWarcry.getDeck(url).validate
      Ok(Json.obj("valid" -> result.isEmpty, "messages" -> result))
    } catch {
      case NonFatal(e) => Ok(Json.obj("valid" -> false, "messages" -> Json.arr(e.getMessage)))
    }
  }

  def validateDecks(tournamentId: String) = Action {
    Ok(views.html.validation(battlefy.listOfPlayers(tournamentId)))
  }

  def generateDeckDoc(tournamentId: String) = Action {
    Ok(views.html.deckdoc(battlefy.getTournament(tournamentId), battlefy.listOfPlayers(tournamentId).map { le =>
      (le._1, le._2.map(eternalWarcry.getDeck))
    }))
  }

  def doc(tournament_id: String): Action[AnyContent] = Action {
    val tournament = battlefy.getTournament(tournament_id)
    val exportFile = docs.doc(tournament, battlefy.listOfPlayers(tournament.battlefy_id).map(i => (i._1, eternalWarcry.getDeck(i._2.get))))
    Ok(Files.readAllBytes(exportFile.toPath))
      .withHeaders("Content-Type" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "content-disposition" -> s"""attachment; filename="${exportFile.getName}"""")
  }

  def side(side: String, link: String, name: String, player: String) = Action {
    graphics.generateImage((player, None), side, eternalWarcry.getDeck(link), Some(name)) match {
      case Right(file) => Ok(Files.readAllBytes(file.toPath)).withHeaders("Content-Type" -> "image/png",
        "content-disposition" -> s"""attachment; filename="${file.getName}"""")
      case Left(error) => NotFound(error.getMessage)
    }
  }

  def left(link: String, name: String, player: String): Action[AnyContent] = side("left", link, name, player)

  def right(link: String, name: String, player: String): Action[AnyContent] = side("right", link, name, player)

  def playersStats = Action {
    Ok(views.html.stats(db.getPlayers))
  }

  def playerStats(playerId: Int) = Action {
    val (name, stats, isRookie) = db.playerStats(playerId)
    Ok(views.html.player(name, stats, isRookie))
  }
}