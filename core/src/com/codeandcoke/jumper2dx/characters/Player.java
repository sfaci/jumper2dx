package com.codeandcoke.jumper2dx.characters;
import com.badlogic.gdx.graphics.g2d.*;
import com.codeandcoke.jumper2dx.screens.MainMenuScreen;
import com.codeandcoke.jumper2dx.managers.SpriteManager;
import com.codeandcoke.jumper2dx.managers.ResourceManager;
import com.codeandcoke.jumper2dx.managers.TiledMapManager;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer.Cell;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;

/**
 * Representa al jugador
 * Las animaciones están basadas en sprites de 18x28 px
 * @author Santiago Faci
 * @version Agosto 2014
 */
public class Player {

	public Vector2 position;
	public State state;
	public Vector2 velocity;
	public boolean canJump;
	public boolean isRunning;
	public boolean isJumping;

    // Plataforma sobre la que actualmente está subido el personaje
	public static Platform stuckPlatform;
	
	// Anota que el personaje ha perdido una vida
	private boolean dead;
	private boolean levelCleared;
	
	private TextureRegion idleLeft;
	private TextureRegion idleRight;
	private TextureRegion walkLeft;
	private TextureRegion walkRight;
	private TextureRegion currentFrame;
	private float stateTime;
	
	private Animation<TextureRegion> rightAnimation;
	private Animation<TextureRegion> leftAnimation;
	
	private SpriteManager spriteManager;
	
	public enum State {
		IDLE_LEFT, IDLE_RIGHT, RUNNING_LEFT, RUNNING_RIGHT
	}
	
	private Array<Rectangle> tiles = new Array<Rectangle>();
	// Pool de rectángulos (mejora la eficiencia si se trabaja con muchos)
	private Pool<Rectangle> rectPool = new Pool<Rectangle>() {
		@Override
		protected Rectangle newObject () {
			return new Rectangle();
		}
	};
	
	// Parámetros de movimiento del personaje
	public static float WALKING_SPEED = 2.0f;
	public static float JUMPING_SPEED = 5.0f;
	public static float MAX_JUMPING = 60f;
	public static float GRAVITY = 9f;
	public static float WIDTH = 18;
	public static float HEIGHT = 28;
	
	public Player(SpriteManager spriteManager) {
		
		position = new Vector2();
		velocity = new Vector2();
		state = State.IDLE_RIGHT;
		this.spriteManager = spriteManager;
        dead = false;
		
		// Posiciones estáticas del personaje para izquierda y derecha en parado y salto
        TextureAtlas atlas = ResourceManager.assets.get("characters/characters.pack", TextureAtlas.class);
		idleLeft = atlas.findRegion("mario_idle_left");
		idleRight = atlas.findRegion("mario_idle_right");
		walkLeft = atlas.findRegion("mario_walk_left", 1);
		walkRight = atlas.findRegion("mario_walk_right", 1);
		
		// Crea la animación para correr hacia la derecha
		rightAnimation = new Animation<TextureRegion>(0.15f, atlas.findRegions("mario_walk_right"));
		
		// Crea la animación para correr hacia la izquierda
		leftAnimation = new Animation<TextureRegion>(0.15f, atlas.findRegions("mario_walk_left"));
	}
	
	/**
	 * Pinta la animación en pantalla
	 */
	public void render(Batch spriteBatch) {
		
		stateTime += Gdx.graphics.getDeltaTime();
		
		// Calcula el frame del personaje que se debe pintar (o qué animación)
		switch (state) {
			case IDLE_LEFT:
				currentFrame = idleLeft;
				break;
			case IDLE_RIGHT:
				currentFrame = idleRight;
				break;
			case RUNNING_LEFT:
				if (isJumping)
					currentFrame = walkLeft;
				else
					currentFrame = leftAnimation.getKeyFrame(stateTime, true);
				break;
			case RUNNING_RIGHT:
				if (isJumping)
					currentFrame = walkRight;
				else
					currentFrame = rightAnimation.getKeyFrame(stateTime, true);
				break;
			default:
				currentFrame = idleLeft;
		}
		
		// Pinta en pantalla el frame actual
		spriteBatch.draw(currentFrame, position.x, position.y, WIDTH, HEIGHT);
	}
	
	/**
	 * Actualiza el estado del jugador en función de la tecla pulsada
	 * @param dt
	 */
	public void update(float dt) {
		
		// Si está pegado a alguna plataforma se desplaza con ella
		/*
		 *  FIXME Al bajar a veces de las nubes luego no choca bien en walkingRight
		 *  Al parecer la culpa la tiene el desplazarse con la nube cuando está pegado
		 *  a ella. No pasa siempre
		 *  Se me ocurre que no habría que hacer alguna cosa cuando esté pegado a una nube,
		 *  algo de este método sobre el eje x
		 */
		if (Player.stuckPlatform != null)
			position.x += Player.stuckPlatform.velocity.x * dt;
		else {
			/*
			 *  Aplica la fuerza de la gravedad (en el eje y) sólo si no está en una plataforma
			 *  En otro caso cae de golpe al bajar de ella (se le acumula gravedad mientras está
			 *  en ella
			 */
			velocity.y -= GRAVITY * dt;
		
			// Controla que el personaje nunca supere una velocidad límite en el eje y
			if (velocity.y > JUMPING_SPEED)
				velocity.y = JUMPING_SPEED;
			else if (velocity.y < -JUMPING_SPEED)
				velocity.y = -JUMPING_SPEED;
		}
		
		// Escala la velocidad para calcular cuanto se avanza en este frame (para mayor precisión)
		velocity.scl(dt);
		
		// Comprueba las colisiones con tiles en el eje X (he quitado + velocity.x en startX, endX)
		Rectangle playerRect = rectPool.obtain();
		playerRect.set(position.x, position.y, WIDTH, HEIGHT);
		int startX, endX, startY, endY;
		// El jugador se desplaza hacia la derecha
		if (velocity.x > 0)
			startX = endX = (int) (position.x + WIDTH + velocity.x); 
		// El jugador se desplaza hacia la izquierda (no se tiene en cuenta la anchura del personaje)
		else
			startX = endX = (int) (position.x + velocity.x);
		
		startY = (int) position.y;
		endY = (int) (position.y + HEIGHT);
		// Obtiene la lista de tiles que ocupan la posición del personaje
		getTilesPosition(startX, startY, endX, endY, tiles);
		playerRect.x += velocity.x;
		for (Rectangle tile : tiles) {
			if (playerRect.overlaps(tile)) {
				velocity.x = 0;
				break;
			}
		}
		playerRect.x = position.x;
		
		// Comprueba las colisiones con tiles en el eje Y (he quitado + velocity.y en startY, endY)
		// El jugador está saltando
		if (velocity.y > 0)
			startY = endY = (int) (position.y + HEIGHT + velocity.y);
		// El jugador cae o está parado (no se tiene en cuenta su altura)
		else
			startY = endY = (int) (position.y + velocity.y);
		
		startX = (int) position.x;
		endX = (int) (position.x + WIDTH);
		// Obtiene la lista de tiles que ocupan la posición del personaje
		getTilesPosition(startX, startY, endX, endY, tiles);
		playerRect.y += velocity.y;
		for (Rectangle tile : tiles) {
			if (tile.overlaps(playerRect)) {
				if (velocity.y > 0) {
					position.y = tile.y - HEIGHT;
				}
				else {
					position.y = tile.y + tile.height;
					canJump = true;
					isJumping = false;
				}
				velocity.y = 0;
				break;
			}
		}
		rectPool.free(playerRect);
		
		velocity.scl(1 / dt);
		position.add(velocity);
	}
	
	/**
	 * El jugador salta
	 * @param sound Indica si debe sonar o no
	 */
	public void jump(boolean sound) {
		
		// Se despega de cualquier plataforma
		Player.stuckPlatform = null;
		
		if (sound)
			ResourceManager.getSound("sounds/jump.wav").play();
		
		velocity.y = Player.JUMPING_SPEED;
		canJump = false;
		isJumping = true;
	}

    /**
     * Check if the player can jump. Then, jump. Otherwise player does nothing
     */
	public void tryJump() {
		Player.stuckPlatform = null;
		if (canJump) {
			jump(true);
		}
	}
	
	/**
	 * El jugador muere
	 */
	public void die() {
		
		dead = true;
		velocity.x = velocity.y = 0;
		spriteManager.CAMERA_OFFSET = 0;
		spriteManager.music.stop();
        ResourceManager.getSound("sounds/player_down.wav").play(0.5f);

		if (spriteManager.levelManager.currentLives == 1) {
			try {
				Thread.sleep(2000);
			} catch (InterruptedException ie) {}
            ResourceManager.getSound("sounds/game_over.wav").play();
            spriteManager.game.getScreen().dispose();
			spriteManager.game.setScreen(new MainMenuScreen(spriteManager.game));
		}
		else {
            spriteManager.levelManager.restartCurrentLevel();
            spriteManager.loadCurrentLevel();
		}
	}
	
	/**
	 * Obtiene una lista de celdas con las que colisiona el personaje
	 * @param startX
	 * @param startY
	 * @param endX
	 * @param endY
	 */
	private void getTilesPosition(int startX, int startY, int endX, int endY, Array<Rectangle> tiles) {
		
		tiles.clear();
		
		float tileWidth = TiledMapManager.collisionLayer.getTileWidth();
		float tileHeight = TiledMapManager.collisionLayer.getTileHeight();
	
		for (int y = startY; y <= endY; y++) {
			for (int x = startX; x <= endX; x++) {
				int xCell = (int) (x / tileWidth);
				int yCell = (int) (y / tileHeight);
				Cell cell = TiledMapManager.collisionLayer.getCell(xCell, yCell);
				
				// Si es un bloque se añade para comprobar colisiones
				if ((cell != null) && (cell.getTile().getProperties().containsKey(TiledMapManager.BLOCKED))) {
					Rectangle rect = rectPool.obtain();
					// El jugador está saltando (se choca con la cabeza en una celda)
					if (velocity.y > 0)
						rect.set(x, y, 1, 1);
					// El jugador está cayendo (se posa en la celda que tenga debajo)
					else
						rect.set((int) (Math.ceil(x / tileWidth) * tileWidth), (int) (Math.ceil(y / tileHeight) * tileHeight), 0, 0);
					
					tiles.add(rect);
					
					if (velocity.y > 0)
						if (cell.getTile().getProperties().containsKey(TiledMapManager.BOX)) {
							
							// Sale el item de la caja
							ResourceManager.getSound("sounds/item_appears.wav").play();
							spriteManager.levelManager.raiseItem((int) (Math.ceil(x / tileWidth) * tileWidth),
												   (int) (Math.ceil(y / tileHeight) * tileHeight));
							
							cell.setTile(TiledMapManager.getEmptyBox(spriteManager.levelManager.map));
						}
				}
				// Si es una moneda, desaparece
				else if ((cell != null) && (cell.getTile().getProperties().containsKey(TiledMapManager.COIN))) {
                    ResourceManager.getSound("sounds/coin.wav").play();
					spriteManager.levelManager.removeCoin(xCell, yCell);
				}
				// Si es un enemigo pierde una vida
				else if ((cell != null) && (cell.getTile().getProperties().containsKey(TiledMapManager.ENEMY))) {
					if (!dead) {
						die();
					}
				}
				// Si es un cofre se abre y se termina la pantalla
				else if ((cell != null) && (cell.getTile().getProperties().containsKey(TiledMapManager.CHEST))) {

					if (!levelCleared) {
						levelCleared = true;

                        spriteManager.music.stop();
                        ResourceManager.getSound("sounds/level_clear.wav").play();
						spriteManager.levelManager.finishCurrentLevel();
                        spriteManager.passCurrentLevel();
					}
				}
			}
		}
	}
}
