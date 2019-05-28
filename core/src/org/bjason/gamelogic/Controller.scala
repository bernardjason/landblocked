package org.bjason.gamelogic

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.{DirectionalLight, DirectionalShadowLight}
import com.badlogic.gdx.graphics.g3d.utils.DepthShaderProvider
import com.badlogic.gdx.graphics.g3d.{Environment, Model, ModelBatch}
import com.badlogic.gdx.graphics.{FPSLogger, PerspectiveCamera, Pixmap, Texture}
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.physics.bullet.Bullet
import com.badlogic.gdx.physics.bullet.collision._
import org.bjason.gamelogic.Log._
import org.bjason.gamelogic.basic.move
import org.bjason.gamelogic.basic.shape.{Basic, BulletShape, CollideShape, MyContactListener, PlayerSprite, Terrain}
import org.bjason.socket.{GameMessage, State, Websocket}
import org.bjason.{gamelogic, socket}

import scala.collection.mutable.ArrayBuffer

object Controller {

  Bullet.init(false, true)

  val myAssets: Array[scala.Tuple2[String, Class[_]]] = Array(
    ("data/basic.jpg", classOf[Texture]),
    ("data/landscape.jpg", classOf[Texture]),
    ("data/cuboid.jpg", classOf[Pixmap]),
    ("data/aliencube.jpg", classOf[Pixmap]),
    ("data/explosion.jpg", classOf[Pixmap]),
    ("data/sky.png", classOf[Pixmap]),
    ("data/hero.g3db", classOf[Model]),
    ("data/8_bit_space_ivader.g3db", classOf[Model])
  )


  lazy val environment = new Environment()
  lazy val cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight())
  val objects: ArrayBuffer[Basic] = ArrayBuffer()

  val deadlist: ArrayBuffer[Basic] = ArrayBuffer()
  val newlist: ArrayBuffer[Basic] = ArrayBuffer()
  lazy val modelBatch = new ModelBatch()
  lazy val spriteBatch = new SpriteBatch();

  lazy val shadowLight = new DirectionalShadowLight(2048, 2048, 1060f, 1460f, .1f, 550f)
  lazy val shadowBatch = new ModelBatch(new DepthShaderProvider());


  lazy val player = PlayerSprite(new Vector3(10, 60, 10), movement = move.Keyboard)
  lazy val skyTexture = new Texture(Gdx.files.internal("data/sky.png"))
  lazy val skyWidth = skyTexture.getWidth
  lazy val skyHeight = skyTexture.getHeight

  val collisionConfig = new btDefaultCollisionConfiguration()
  val dispatcher = new btCollisionDispatcher(collisionConfig)
  val broadphase = new btDbvtBroadphase()
  val collisionWorld = new btCollisionWorld(dispatcher, broadphase, collisionConfig)
  val contactListener = new MyContactListener()
  val shape: ArrayBuffer[CollideShape] = ArrayBuffer()
  val bulletlist: ArrayBuffer[CollideShape] = ArrayBuffer()
  val mycontactLisstener = new MyContactListener

  def addBulletObjectToDispose(bulletObject: CollideShape) {
    bulletlist += bulletObject
  }

  def doBulletDispose {
    for (b <- bulletlist) {
      b.dispose
    }
    bulletlist.clear
    collisionWorld.release()
  }

  val terrains = scala.collection.mutable.Map[String, Terrain]()
  var lastPitUsed = 0


  objects ++= Array(
    player
  )

  def maxObjects = objects.size

  def create {
    gamelogic.Common.loadAssetsForMe(myAssets)
    socket.Websocket.test

    cameraEnvironment

    Gdx.graphics.setTitle("Player " + Basic.playerPrefix)

    info("Game " + "Player " + Basic.playerPrefix + "  starting")

    for (t <- terrains.values) {
      t.init
      t.reset
    }

    for (o <- objects) o.init

    for (o <- objects) o.reset

    socket.Websocket.connect(socket.Websocket.Login(gamelogic.GameSetup.playerPrefix.toString, ""+gamelogic.GameSetup._playerId, gamelogic.GameSetup.gameName))

    socket.Websocket.jsonobjects += player.jsonObject.get

  }

  val fps = new FPSLogger()
  var ticker = 0
  var skyAddX = 0

  def render() {

    Websocket.clearDeletedObjects
    socket.Websocket.sayHello
    socket.Websocket.writeAllMyObjects()
    socket.Websocket.readOtherObjects()


    gamelogic.Common.refreshTtlTime
    ticker = ticker + 1

    if ( ticker % 60 == 0 ) {
      Websocket.broadcastMessage(GameMessage(objId = ""+gamelogic.GameSetup.playerPrefix , msg=GameInformation.send))
    }

    skyAddX = skyAddX + (Gdx.graphics.getDeltaTime * InputHandler.dir * 600).toInt
    val forSkyY = player.position.y / 2
    val forSkyX = skyAddX // +  player.position.x / 4
    spriteBatch.begin()

    for (skyY <- (forSkyY % skyHeight).toInt - skyHeight to (forSkyY % skyHeight).toInt + skyHeight by skyHeight) {
      for (skyX <- (forSkyX % skyWidth).toInt - skyWidth to (forSkyX % skyWidth).toInt + skyWidth by skyWidth) {
        spriteBatch.draw(skyTexture, skyX, skyHeight - skyY)
      }
    }
    spriteBatch.end()


    addSurroundingTerrains


    for (o <- objects) {
      o.move(objects.toList)
    }

    for (o <- objects) {

      if (o.shape.bullet && o.shape.bullet) {
        val s = o.shape.asInstanceOf[BulletShape]
        if (s.bulletObject != null) s.bulletObject.setWorldTransform(o._getTransform)

      }
    }

    collisionWorld.performDiscreteCollisionDetection()


    cam.position.set(player.position)
    cam.translate(InputHandler.direction.cpy().scl(250))
    cam.lookAt(player.position)
    cam.position.y = player.position.y + 50
    cam.update()

    doShadow()
    modelBatch.begin(cam)

    val drawTerrains = 1
    for (zz <- -Terrain.terrainSize * drawTerrains to Terrain.terrainSize * drawTerrains by Terrain.terrainSize) {
      for (xx <- -Terrain.terrainSize * drawTerrains to Terrain.terrainSize * drawTerrains by Terrain.terrainSize) {
        terrains.get(Terrain.positionToKey(player.position.x + xx, player.position.z + zz)).map { t1 =>
          t1.move()
          t1._render(modelBatch, environment, cam)
        }
      }
    }

    for (o <- objects) {
      if (o.shape.isVisible(o.instance.transform, cam)) {
        o._render(modelBatch, environment, cam)
      }
    }

    modelBatch.end()

    spriteBatch.begin()
    GameInformation.drawText(spriteBatch)
    spriteBatch.end()


    doDeadList
    doNewList
    doBulletDispose

    fps.log()

    GameInformation.setAliens( Websocket.jsonobjects.filter( _.what == "EightBitInvader").size )

    if ( GameInformation.isGameEnd ) {
      GameProxy.endGameScreen
    }
  }


  val tempPosition = new Vector3

  def doShadow() = {
    player.instance.transform.getTranslation(tempPosition)
    tempPosition.y = tempPosition.y - 100
    shadowLight.begin(tempPosition, cam.direction);
    shadowBatch.begin(shadowLight.getCamera());
    shadowBatch.render(player.instance)

    val drawTerrains = 1
    for (zz <- -Terrain.terrainSize * drawTerrains to Terrain.terrainSize * drawTerrains by Terrain.terrainSize) {
      for (xx <- -Terrain.terrainSize * drawTerrains to Terrain.terrainSize * drawTerrains by Terrain.terrainSize) {
        terrains.get(Terrain.positionToKey(player.position.x + xx, player.position.z + zz)).map { t1 =>
          t1.renderShadow(shadowBatch, cam)
        }
      }
    }

    shadowBatch.end();
    shadowLight.end();
  }

  val EASIER_TO_DEBUG =   (Terrain.terrainSize * 1).toInt
  private def addSurroundingTerrains = {
    val give = EASIER_TO_DEBUG
    for (zzz <- player.position.z.toInt - give to player.position.z.toInt + give by Terrain.terrainSize) {
      for (xxx <- player.position.x.toInt - give to player.position.x.toInt + give by Terrain.terrainSize) {
        val x = player.position.x + xxx
        val z = player.position.z + zzz
        if (x > -Terrain.MAXX+Terrain.terrainSize && x < Terrain.MAXX-Terrain.terrainSize &&
          z > -Terrain.MAXZ+Terrain.terrainSize && z < Terrain.MAXZ-Terrain.terrainSize) {

          val key = Terrain.positionToKey(x, z)
          terrains.getOrElseUpdate(key, createTerrainForKey(x, z, key) )
        } else {
          val key = Terrain.positionToKey(x, z)
          terrains.getOrElseUpdate(key, {
            val v3 = Terrain.positionToKeyVector3(x, z)
            v3.scl(Terrain.terrainSize)
            val t = new Terrain(key, v3, "data/landscape.jpg",land = false)
            t.init
            t.reset
            t
          }
          )

        }
      }
    }
  }

  def createTerrainForKey(x: Float, z: Float, key: String) = {

    val v3 = Terrain.positionToKeyVector3(x, z)
    v3.scl(Terrain.terrainSize)
    val t = new Terrain(key, v3, "data/landscape.jpg")
    t.init
    t.reset
    t

  }

  def dispose {
    modelBatch.dispose()

  }

  def changeFocus(objectNumber: Int) {
    for (o <- objects) o.movement = move.NoMovement

    objects(objectNumber).movement = move.Keyboard
  }


  def cameraEnvironment {
    environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f))
    environment.set(new ColorAttribute(ColorAttribute.Fog, 0.48f, 0.69f, 1f, 0.18f))
    environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f))

    environment.add(shadowLight.set(0.8f, 0.8f, 0.8f, -0f, -0.8f, -0.1f))
    environment.shadowMap = shadowLight
    cam.position.set(0, 10, -50)
    cam.lookAt(0, 0, 0)
    cam.near = 1f
    cam.far = 1000
    cam.update()

  }


  def addNewBasic(basic: Basic) {
    newlist += basic
  }

  def addToDead(remove: Basic) {
    deadlist += remove
    remove.jsonObject.map{ x => x.state = Some(State.DEAD) }
  }

  def doDeadList {
    for (d <- deadlist) {
      objects -= d
      addBulletObjectToDispose(d.shape)
      d.dead = true
    }
    deadlist.clear()
  }

  def doNewList {
    for (b <- newlist) {
      b.init
      b.reset
      objects += b
    }
    newlist.clear()
  }

  def pause() {
    System.exit(0)
  }






}
