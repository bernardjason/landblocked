package org.bjason.socket

import com.badlogic.gdx.math.{Matrix4, Vector3}
import com.badlogic.gdx.utils.{Json, JsonValue}
import org.bjason.gamelogic
import org.bjason.gamelogic.Log
import org.bjason.gamelogic.basic.move.NoMovement
import org.bjason.gamelogic.basic.shape
import org.bjason.socket.State.State

object State extends Enumeration {
  type State = Value
  val NONE, ALIVE, DEAD = Value

  def fromValue(string: String): Option[State] = {
    return values.find(_.toString == string)
  }
}

case class JsonObject(
                       var what: String = null,
                       var id: String = null,
                       var changed: Int = 0,
                       var state: Option[State] = None,
                       var instance: Matrix4 = null
                     )
  extends Json.Serializable {

  var whenDeadLinger = 2

  def this() {
    this(null, null, 0, null, null)
  }


  def dead: Unit = {
    state = Some(State.DEAD)
    Log.info("*** SET TO DEAD ", what, id, state)
  }


  def write(json: Json): Unit = {
    json.writeValue("what", what)
    json.writeValue("id", id)
    json.writeValue("changed", changed)
    json.writeValue("state", state.getOrElse("").toString)
    json.writeValue("instance", instance)
    changed = changed -1 ;
    if ( changed < 0 ) changed = gamelogic.Common.UNCHANGED

  }

  def read(json: Json, jsonMap: JsonValue) = {
    what = jsonMap.get("what").asString()
    id = jsonMap.get("id").asString()
    //changed = jsonMap.get("changed").asInt()
    state = State.fromValue(jsonMap.get("state").asString())
    instance = new Matrix4(jsonMap.get("instance").get("val").asFloatArray())
  }

  def toObject() = {
    //changed = Common.UNCHANGED
    var exists = false
    if (!id.startsWith("C")) {

      for (o <- gamelogic.Controller.objects) {
        if (o.id == id) {
          exists = true
          if (state.get == State.DEAD) {
            gamelogic.Controller.addToDead(o)
            Log.info("REMOVE OBJECT ", this)
          } else {
            o.instance.transform.set(instance)
            if (o.shape.bulletObject != null) o.shape.bulletObject.setWorldTransform(o._getTransform)
            o.instance.transform.getTranslation(o.position)
            o.animate
          }
        }
      }
    } else {
      /* YOU COULD JUMP TO TERRAIN WITH KEY */
      for (t1 <- gamelogic.Controller.terrains.values) {
        t1.objects.map(o =>
          if (o.id == id) {
            exists = true
            if (o.dead == false && state.get == State.DEAD) {
              gamelogic.Controller.addToDead(o)
            }
          }
        )
      }
      if (!exists && state.get == State.ALIVE && !Websocket.jsonobjects.exists(o => o.id == id)) {
        val terrainId = id.substring(0, id.indexOf(shape.Terrain.SEPARATOR)).substring(2)

        if (gamelogic.Controller.terrains.isDefinedAt(terrainId)) {

          val t = gamelogic.Controller.terrains(terrainId)

          // ****** BIT OF A MESS, CALC BETTER println(id+" BERNARD "+terrainId+"   "+t.key)
          //Websocket.dumpJsonObjects(true)
          //println(s"BERNARD ADD ${exists} ${id} ${state} ${what}");
          val r = what match {
            case "LandedInvader" => Some(new shape.LandedInvader(startPosition = new Vector3(), id = id))
            case _ => None
          }
          r.map { o =>
            //Log.info(" making sure it is " + o.id)
            o.instance.transform.set(instance)
            o.originalMatrix4.set(instance)
            if (o.shape.bulletObject != null) o.shape.bulletObject.setWorldTransform(o._getTransform)
            t.objects += o
            o.jsonObject.map{o => Websocket.jsonobjects += o}
          }
        } else {
          println("*********************************************************")
          println("*********************************************************")
          println(terrainId,id)
          println("*********************************************************")
          println("*********************************************************")
          System.exit(0)
        }
      }
    }


    if (!exists && state.get != State.DEAD) {

      Log.info(s"${gamelogic.GameSetup.playerPrefix} ID is ${id} ${this.what},${this.id},${this.state}")
      val r = what match {
        case "PlayerSprite" => Some(shape.PlayerSprite(new Vector3(), movement = NoMovement, id = id))
        case "MissileShape" => Some(shape.MissileShape(new Vector3(), movement = NoMovement, id = id))
        // dont think needed case "EightBitInvader" => Some(new EightBitInvader(startPosition = new Vector3(),  id = id))
        case _ => None

      }

      r.map { o =>
        //Log.info(" making sure it is " + o.id)
        o.instance.transform.set(instance)
        o.originalMatrix4.set(instance)
        if (o.shape.bulletObject != null) o.shape.bulletObject.setWorldTransform(o._getTransform)
        gamelogic.Controller.addNewBasic(o)
      }
    }
  }

}
