package types

import org.htmlcleaner.{HtmlCleaner, TagNode}

case class Deck(
                 link: String,
                 description: String,
                 name: String,
                 mainDeck: List[(Card, Int)],
                 sideBoard: List[(Card, Int)],
                 market: List[(Card, Int)],
                 notTournamentDeck: Boolean,
                 userDefinedArchetype: Option[String]
               ) {
  val isTournamentDeck: Boolean = !notTournamentDeck

  def hasBlackMarket: Boolean = {
    val mainCards = mainDeck.map(_._1)
    val marketCards = market.map(_._1)
    !marketCards.exists(card => mainCards.contains(card))
  }

  def eternalFormat: List[String] = {
    List("Main Deck:") ++ this.mainDeck.map(p => s"${p._2} ${p._1.eternalFormat}") ++
      List(" ", "--------------MARKET---------------") ++ this.market.map(p => s"${p._2} ${p._1.eternalFormat}")
  }

  def validate: List[String] = {
    var messages = List[String]()
    if (this.notTournamentDeck) messages = messages :+ "Is not a tournament deck"
    this.market.filterNot(p => p._1.name.contains("Sigil")).foreach(p => {
      val mainDeckCopies = mainDeck.find(e => e._1.equals(p._1))
      val numberOfCopies: Int = p._2 + mainDeckCopies.map(_._2).getOrElse(0)
      if (numberOfCopies > 4) {
        messages = messages :+ s"$numberOfCopies copies of ${p._1.eternalFormat}"
      }
    })
    if (this.market.map(_._2).sum > 5) messages = messages :+ s"Market has ${this.market.size} cards"
    if (market.exists(p => p._2 > 1)) messages = messages :+ "Market has duplicates"
    val mainDeckSize = this.mainDeck.map(_._2).sum
    if (mainDeckSize < 75 || mainDeckSize > 150) messages = messages :+ s"Size of the main deck is ${this.mainDeck.size}"
    val sideBoardSize = this.sideBoard.map(_._2).sum
    if (sideBoardSize > 0) messages = messages :+ s"Size of the sideboard is $sideBoardSize"
    messages
  }

  def cards: List[(Card, Int)] = mainDeck ++ market ++ sideBoard

  def eternalWarCryId: String = {
    val r1 = "details\\/(.+)\\/".r
    val r2 = "\\/d\\/(.+)\\/".r
    r1.findFirstIn(link) match {
      case None => r2.findAllIn(link).matchData.toList.head.group(1)
      case Some(_) => r1.findAllIn(link).matchData.toList.head.group(1)
    }
  }

  def faction: Option[String] = cards.flatMap(_._1.influences.toList).distinct.sortBy(f => f.toString.charAt(0)) match {
    case inf if inf.length == 1 => Some(inf.head.toString.toLowerCase.capitalize)
    case l if l.length == 2 && l.contains(Faction.SHADOW) && l.contains(Faction.TIME) => Some("Xenan")
    case l if l.length == 2 && l.contains(Faction.SHADOW) && l.contains(Faction.PRIMAL) => Some("Feln")
    case l if l.length == 2 && l.contains(Faction.SHADOW) && l.contains(Faction.JUSTICE) => Some("Argenport")
    case l if l.length == 2 && l.contains(Faction.SHADOW) && l.contains(Faction.FIRE) => Some("Stonescar")

    case l if l.length == 2 && l.contains(Faction.JUSTICE) && l.contains(Faction.FIRE) => Some("Rakano")
    case l if l.length == 2 && l.contains(Faction.TIME) && l.contains(Faction.JUSTICE) => Some("Combrei")
    case l if l.length == 2 && l.contains(Faction.PRIMAL) && l.contains(Faction.JUSTICE) => Some("Hooru")

    case l if l.length == 2 && l.contains(Faction.FIRE) && l.contains(Faction.TIME) => Some("Praxis")
    case l if l.length == 2 && l.contains(Faction.FIRE) && l.contains(Faction.PRIMAL) => Some("Skycrag")

    case l if l.length == 2 && l.contains(Faction.TIME) && l.contains(Faction.PRIMAL) => Some("Elysian")

    case l if l.length == 3 && l.contains(Faction.JUSTICE) && l.contains(Faction.TIME) && l.contains(Faction.PRIMAL) => Some("TJP")

    case l if l.length == 3 && l.contains(Faction.FIRE) && l.contains(Faction.JUSTICE) && l.contains(Faction.PRIMAL) => Some("Ixtun(FJP)")
    case l if l.length == 3 && l.contains(Faction.FIRE) && l.contains(Faction.TIME) && l.contains(Faction.PRIMAL) => Some("Jennev(FTP)")
    case l if l.length == 3 && l.contains(Faction.FIRE) && l.contains(Faction.JUSTICE) && l.contains(Faction.SHADOW) => Some("Winchest(FJS)")
    case l if l.length == 3 && l.contains(Faction.TIME) && l.contains(Faction.JUSTICE) && l.contains(Faction.SHADOW) => Some("Kerendon(TJS)")
    case l if l.length == 3 && l.contains(Faction.TIME) && l.contains(Faction.PRIMAL) && l.contains(Faction.SHADOW) => Some("Auralian(TPS)")

    case inf if inf.length > 1 => Some(inf.map(f => f.toString.charAt(0).toUpper).mkString)
    case _ => None
  }

  def archetype: Option[String] = faction.map(f => s"$f ${userDefinedArchetype.getOrElse(name.split("-").map(_.capitalize).filterNot(word => faction.contains(word)).mkString(" "))}")
}

object Deck {
  private val TOURNEY_IDENTIFIER = "<i class=\"fa fa-trophy\">"

  def parse(url: String, htmlSource: String): Deck = {
    val htmlCleaner = new HtmlCleaner
    val node = htmlCleaner.clean(htmlSource)
    val additionalCards = node.evaluateXPath("//*[@id='div-list']/*").map(_.asInstanceOf[TagNode]).filter(t => t.getAttributeByName("id") != "maindeck-wrapper").toList
    val archetypeValue = node.evaluateXPath("//tr[td[text()='Archetype']]/td[2]").headOption.map(_.asInstanceOf[TagNode]).map(_.getText.toString).filterNot(_ == "Unknown")
    val M = "Market"
    val S = "Sideboard"
    val markers = additionalCards.map(p => p.getText.toString).map {
      case t if t.contains(M) => M
      case t if t.contains(S) => S
      case _ => ""

    }
    val (sideboard, market): (List[TagNode], List[TagNode]) = (markers.indexOf("Sideboard"), markers.indexOf("Market")) match {
      case (-1, -1) => (List(), List())
      case (-1, 0) => (List(), additionalCards)
      case (0, -1) => (additionalCards, List())
      case (0, n) => (additionalCards.take(n), additionalCards.slice(n, additionalCards.length))
      case (n, 0) => (additionalCards.slice(n, additionalCards.length), additionalCards.take(n))
    }
    Deck(
      link = url,
      description = "Description?",
      name = url.split("/").last,
      mainDeck = cardsList(node.evaluateXPath("//*[@id='maindeck-wrapper']/a").toList.map(_.asInstanceOf[TagNode])),
      sideBoard = cardsList(sideboard.filterNot(t => t.getText.toString.contains(S) || t.getText.toString.contains(M))),
      market = cardsList(market.filterNot(t => t.getText.toString.contains(S) || t.getText.toString.contains(M))),
      notTournamentDeck = !htmlSource.contains(TOURNEY_IDENTIFIER),
      archetypeValue
    )
  }

  def parse(eternalFormat: String): (List[String], List[String]) = {
    val parts: List[String]  = eternalFormat.split("--------------MARKET---------------\n").toList
    val mainDeckText = parts.head
    (mainDeckText.split("\n").toList, if(parts.length>1) parts(1).split("\n").toList else List())
  }

  private def cardsList(nodes: List[TagNode]) = nodes.map(tag => (Card.parse(tag), tag.getAttributeByName("data-count").toInt))

  val empty: Deck = Deck("empty", "empty", "empty", List(), List(), List(), notTournamentDeck = false, Some("empty"))

}
