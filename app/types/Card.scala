package types

import org.htmlcleaner.TagNode

case class Card(
                 name: String,
                 influence: Map[Faction.Value, Int],
                 cost: Int,
                 set: String,
                 eternalId: String,
                 isPower: Boolean
               ) {
  def eternalFormat: String = s"$name (Set$set #$eternalId)"

  def influences: Set[Faction.Value] = influence.filter(_._2 != 0).keySet
}

object Card {
  private val ASCII = "&#(\\d+);".r

  def parse(tag: TagNode): Card = Card(
    decode(tag.getAttributeByName("data-name")),
    Faction.values.map(faction => (faction, tag.getAttributeByName("data-" + faction.toString.toLowerCase()).toInt)).toMap,
    tag.getAttributeByName("data-cost").toInt,
    tag.getAttributeByName("data-set"),
    tag.getAttributeByName("data-eternalid"),
    "power".equalsIgnoreCase(tag.getAttributeByName("data-group")))

  def decode(text: String): String = {
    var modifiedText = text
    for (t <- ASCII.findAllIn(modifiedText)) {
      modifiedText = modifiedText.replace(t, Character.toString(t.toInt.toChar))
    }
    modifiedText.replaceAll("&amp;", "&")
  }

  def parseDetails(cardText: String): (Int, String, Int, Int) = {
    val numberOfCards = cardText.substring(0, cardText.indexOf(" ")).toInt
    val r1 = cardText.substring( cardText.indexOf(" ")).trim
    val cardName = r1.substring(0, r1.indexOf("(")).trim
    val r3 = r1.substring(r1.indexOf("(Set")+4, r1.indexOf(")")).split(" #")
    val set = r3.head
    val num = r3(1)
    (numberOfCards, cardName, set.toInt, num.toInt)
  }
}