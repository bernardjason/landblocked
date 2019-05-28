package org.bjason.gamelogic.desktop;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import org.bjason.gamelogic.EndScreen;
import org.bjason.gamelogic.GameProxy$;
import org.bjason.gamelogic.GameSetup$;
import org.bjason.gamelogic.GameInformation$;

import java.io.IOException;


public class TestEndScreen {
	public static void main (String[] arg) throws IOException {

		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();


        config.y=0;


		config.width=1024;
		config.height=800;
		//config.fullscreen=true;
		GameProxy$ game = GameProxy$.MODULE$;
		GameInformation$.MODULE$.allScore().put("1","Player 1 Score 10 hits 0");
		GameInformation$.MODULE$.allScore().put("2","Player 2 Score 3 hits 0");
		GameInformation$.MODULE$.allScore().put(
				GameSetup$.MODULE$.playerPrefix()+"",
				"Player "+GameSetup$.MODULE$.playerPrefix()+" Score 123 hits 0");
		GameInformation$.MODULE$.allScore().put("3","Player 3 Score 6 hits 0");
		game.setAdapter( new EndScreen());
		LwjglApplication l = new LwjglApplication(game, config);
		Gdx.app.setLogLevel(Application.LOG_INFO);


		//new LwjglApplication(game, config);
	}
}
