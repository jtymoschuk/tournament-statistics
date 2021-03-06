package controllers

import java.util.concurrent.TimeUnit

import javax.inject.Inject
import org.joda.time.DateTime
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc.Controller
import sx.blah.discord.handle.obj.IUser
import types.Tournament

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.language.implicitConversions
import scala.util.control.NonFatal

class Battlefy @Inject()(ws: WSClient) extends Controller {

  type EternalLink = String
  type EternalName = String
  type BattlefyId = String
  type DiscordName = String

  def all_tournaments: () => String = () => "https://dtmwra1jsgyb0.cloudfront.net/organizations/5a0e00fdc4cd48033c0083b7/tournaments"

  def auth(client_id: String) = s"https://auth0.battlefy.com/authorize?client_id=$client_id&response_type=token"

  def stage_info: String => String = (stage: String) => s"https://dtmwra1jsgyb0.cloudfront.net/stages/$stage/matches"

  def players: String => String = (tournament_id: String) => s"https://dtmwra1jsgyb0.cloudfront.net/tournaments/$tournament_id/teams"

  def checkIn: (String, String) => String = (tournament_id: String, player_id: String) => s"https://api.battlefy.com/tournaments/$tournament_id/teams/$player_id/check-in"

  def currentOpponent(name: String): Option[String] = currentOpponents.filter(o => o._1.contains(name) || o._2.contains(name)).flatMap(o => if (o._1.contains(name)) o._2 else o._1).headOption

  def currentOpponents: List[(Option[String], Option[String])] = {
    val roundNumber = currentRoundNumber
    val info = currentStageId.map(stageInfo(_).value.filter(i => roundNumber.contains(i.asInstanceOf[JsObject].value("roundNumber").as[Int]))).getOrElse(Seq())
    info.map(o => o.as[JsObject].value("top").as[JsObject] -> o.as[JsObject].value("bottom").as[JsObject])
      .map(o => (if (o._1.keys.contains("team")) Some(o._1) else None) -> (if (o._2.keys.contains("team")) Some(o._2) else None))
      .map(o => o._1.map(_.value("team").as[JsObject].value("name").as[String]) -> o._2.map(_.value("team").as[JsObject].value("name").as[String])).toList
  }

  def currentRoundNumber: Option[Int] = {
    val stage = currentStageId
    val round = stage.map(stageInfo(_).value).toList.flatten.map(_.asInstanceOf[JsObject]).filter(_.value("top")
      .asInstanceOf[JsObject].\\("team").nonEmpty).filter { o =>
      !o.fieldSet.map(_._1).contains("isComplete") ||
        !o.value("isComplete").asInstanceOf[JsBoolean].value
    }.map(_.value("roundNumber").as[JsNumber].value.intValue())
    if (round.isEmpty) None else Some(round.min)
  }

  def currentRoundTitle: Option[String] = {
    currentRound.map(rn => s"Round ${rn._1} ${rn._2.capitalize}")
  }

  def currentRound: Option[(Int, String)] = {
    val bracket = currentStageInfo.map(_.as[JsObject].value("bracket").as[JsObject].value("type").as[JsString].value)
    currentRoundNumber.flatMap(rn => bracket.map(b => (rn, b)))
  }

  def listOfPlayers(tournamentId: String): List[(EternalName, Option[EternalLink], Option[DiscordName], BattlefyId)] =
    playersInfo(tournamentId).map(p => {
      val battlefyId = (p \ "_id").as[String]
      val eternalName = (p \ "name").as[String]
      val customFields = (p \ "customFields" \\ "value").toList.map(_.as[String])
      (eternalName, customFields.find(_.contains("eternalwarcry")), customFields.filterNot(_.contains("eternalwarcry")).headOption, battlefyId)
    })

  def playersInfo(tournamentId: String): List[JsValue] =
    Await.result(ws.url(players(tournamentId)).get().map(response => {
      Json.parse(response.body).asInstanceOf[JsArray].value.toList
    }), Duration.apply(30, TimeUnit.SECONDS))

  def getTournament(battlefy_uuid: String): Tournament = {
    val t = getTournamentInfo(battlefy_uuid).get
    Tournament(
      t.value("_id").as[String],
      t.value("name").as[String],
      DateTime.parse(t.value("startTime").as[String]),
      None,
      None,
      None,
      checkInStarted = false,
      registrationEnabled = t.value("registrationEnabled").as[Boolean],
      Some(DateTime.parse(t.value("checkInStartTime").as[String])))
  }

  def getTournamentInfo(battlefy_uuid: String): Option[JsObject] = {
    Await.result(ws.url(all_tournaments()).get().map(response => {
      Json.parse(response.body).asInstanceOf[JsArray].value.toList
        .map(_.as[JsObject]).find(t => t.value("_id").as[String] == battlefy_uuid)
    }), Duration.apply(30, TimeUnit.SECONDS))
  }

  def currentStageId: Option[String] = currentStageInfo.map(_.asInstanceOf[JsObject].value("_id").as[String])

  def currentStageInfo: Option[JsValue] = getTournamentInfo(getCurrentTournament.battlefy_id).get.value("stages").asInstanceOf[JsArray]
    .value.toList.sortBy(o => o.asInstanceOf[JsObject].value("startTime").as[String]).reverse.headOption

  def stageInfo(stage: String): JsArray = Await.result(ws.url(stage_info(stage)).get().map(response => {
    Json.parse(response.body).asInstanceOf[JsArray]
  }), Duration.apply(30, TimeUnit.SECONDS))

  def getCurrentTournament: Tournament = {
    implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)

    Await.result(ws.url(all_tournaments()).get().map(response => {
      val tournament = Json.parse(response.body).asInstanceOf[JsArray].value.toList.sortBy(o => DateTime.parse(o.\("startTime").as[String])).reverse.head
      val id = tournament.\("_id").as[String]
      val name = tournament.\("name").as[String]
      val date = DateTime.parse(tournament.\("startTime").as[String])
      val checkInStartTime = tournament.\("checkInStartTime").toOption.map(v => DateTime.parse(v.as[String]))
      val checkInStarted = checkInStartTime.exists(_.isBeforeNow)
      val registrationEnabled = tournament.\("registrationEnabled").as[Boolean]
      Tournament(id, name, date, None, checkInStarted = checkInStarted, event_type = None, registrationEnabled = registrationEnabled, checkInStartTime = checkInStartTime)
    }), Duration.apply(30, TimeUnit.SECONDS))
  }

  def getAuthToken(client_id: String): Option[String] = {
    Await.result(ws.url(auth(client_id)).get().map(response => {
      try {
        val body = response.body
        val str = body.split("var authParams = ")(1).split("\n")(0)
        Some(str.substring(str.indexOf("'") + 1, str.lastIndexOf("'")))
      } catch {
        case NonFatal(e) => None
      }
    }), Duration.apply(30, TimeUnit.SECONDS))
  }

  def games(battlefy_id: String): List[(String, String, Int, Int, Int, String, Boolean)] = getTournamentInfo(battlefy_id) match {
    case Some(tournament) =>
      (tournament \ "stages").as[JsArray].value.toList.par.flatMap { stage =>
        val s_id = (stage \ "_id").as[String]
        val stageType = (stage \ "bracket" \ "type").as[String]
        stageInfo(s_id).value.toList.filter(game => (game \ "top" \ "team").toOption.isDefined).map { game =>
          val roundNumber = (game \ "roundNumber").as[JsNumber].value.intValue()
          val player1Name = (game \ "top" \ "team" \ "name").as[String]
          val (player1Score, player2Name, player2Score) = if ((game \ "isBye").as[Boolean]) (2, Battlefy.BYE, 0) else (
            (game \ "top" \ "score").toOption.map(_.as[JsNumber].value.intValue()).getOrElse(0),
            (game \ "bottom" \\ "team").headOption.map( o => (o \ "name").as[String]).getOrElse(""),
            (game \ "bottom" \ "score").toOption.map(_.as[JsNumber].value.intValue()).getOrElse(0)
          )
          val complete = (game \ "isComplete").toOption.exists(_.as[JsBoolean].value)
          (player1Name, player2Name, player1Score, player2Score, roundNumber, stageType, complete)
        }
      }.toList
    case _ => List.empty
  }
}

object Battlefy {

  implicit def uToP(user: IUser): Participant = Participant(user)

  val BYE = "BYE+0000"
}

case class Participant(user: IUser) {
  def isParticipant(battlefy: Battlefy): Boolean = {
    val players = battlefy.listOfPlayers(battlefy.getCurrentTournament.battlefy_id)
    players.exists(player => player._3.isDefined && player._3.get.startsWith(s"${user.getName}#"))
  }
}
