package com.nightshift.game.screens;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapRenderer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.nightshift.game.sprites.Ghost;
import com.nightshift.game.sprites.Janitor;
import com.nightshift.game.NightShift;
import com.nightshift.game.data.Constants;
import com.nightshift.game.data.MapData;
import java.util.ArrayList;

public class GameScreen implements Screen {

    //MapData returns enemy spawn locations and map file name based on a level index.
    private static MapData mapData = new MapData();
    private int levelIndex;

    //Used to draw Sprite objects on the screen.
    private SpriteBatch batch;
    private ArrayList<Ghost> enemies;

    private Janitor hero;

    //TODO: define the viewport
    private FitViewport viewport;
    private TiledMap map;
    private TiledMapRenderer tiledMapRenderer;
    //Map layer that contains the end location stored as a RectangleMapObject.
    private MapLayer winLayer;
    //Map layer that contains the walls in MapObjects.
    private MapLayer wallLayer;
    //Object that stores the walls as an array of RectangleMapObject.
    private MapObjects mapObjects;

    //Provides a reference to NightShift for access to LifeBar and to setScreen() method.
    private NightShift game;
    //World used for Box2d physics.
    private World world;

    public GameScreen(NightShift game, int levelIndex) {
        this.game = game;
        //Initializes a world with no gravity.
        this.world = new World(new Vector2(0, 0), true);
        this.levelIndex = levelIndex;

        map = new TmxMapLoader().load(mapData.getFileName(levelIndex));
        tiledMapRenderer = new OrthogonalTiledMapRenderer(map);
        wallLayer = map.getLayers().get(1);
        winLayer = map.getLayers().get(2);
        mapObjects = wallLayer.getObjects();

        viewport = new FitViewport(Constants.VIEWPORT_WIDTH, Constants.VIEWPORT_HEIGHT, game.camera);
        viewport.setScreenHeight(Gdx.graphics.getHeight());
        viewport.setScreenWidth(Gdx.graphics.getWidth());

        Vector2 spawn = mapData.janitorSpawn(levelIndex);
        this.hero = new Janitor((int)spawn.x, (int)spawn.y, this);
        this.batch = new SpriteBatch();
        spawnEnemies();
    }

    @Override
    public void render(float delta) {
        //Applies velocity to all Sprites to queue a positional change to be carried out during world.step().
        //Also updates invulnerability timer before world.step() to ensure that user is able to take damage if applicable.
        hero.moveJanitor();
        hero.updateInvulnerabilityTimer();
        for(Ghost g: enemies) {
            g.moveGhost();
        }

        //Applies positional change based on velocity applied over a very small time interval (1/60th of a second). Checks
        //if user has reached the end of the map and for contact with enemies directly after the world.step().
        world.step(1f / 60f, 6, 2);
        checkWin();
        checkGhostCollisions();

        //Updates every Sprite's position to match the Body's position, as world.step() operates on Body's position
        //as part of the Box2D physics engine.
        hero.updateJanitorPosition();
        for(Ghost g: enemies) {
            g.updateGhostPosition();
        }

        //Prepares the screen and camera to display all Sprites in their correct positions for this iteration of render.
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        game.camera.update();
        tiledMapRenderer.setView(game.camera);
        tiledMapRenderer.render();

        //Scales and then draws all Sprites onto the screen.
        batch.begin();
        batch.setProjectionMatrix(game.camera.combined);
        game.health.draw(batch);
        hero.draw(batch);
        for(Ghost g: enemies) {
            g.draw(batch);
        }
        batch.end();

        //Resets velocity for all Sprites to zero.
        hero.resetVelocity();
        for(Ghost g: enemies) {
            g.resetVelocity();
        }
    }

    private void spawnEnemies() {
        /**
         * Spawn an array of ghost according to there positions on different maps.
         * Information about maps is passed in from the MapData class.
         */
        enemies = new ArrayList<Ghost>();
        for(Vector2 pos: mapData.getEnemySpawnLocations(levelIndex)) {
            enemies.add(new Ghost(hero, pos.x, pos.y, world));
        }
    }

    //Determines, based on the player's current direction, whether or not a collision will occur with map walls and
    //restricts movement accordingly.
    public boolean mapCollisionWillOccur() {
        /**
         * Makes a rectangle around janitor and uses intersector to check janitor's collision with walls.
         */
        int threshold = 10;
        Rectangle player = new Rectangle(0,0,0,0);

        switch(hero.getDirection()) {
            case BACK:
                player = new Rectangle(hero.getX(),hero.getY(),hero.getDimensions().x,hero.getDimensions().y + threshold);
                break;
            case LEFT:
                player = new Rectangle(hero.getX() - threshold + 6,hero.getY(),hero.getDimensions().x,hero.getDimensions().y);
                break;
            case FRONT:
                player = new Rectangle(hero.getX(),hero.getY() - threshold + 6,hero.getDimensions().x,hero.getDimensions().y);
                break;
            case RIGHT:
                player = new Rectangle(hero.getX(),hero.getY(),hero.getDimensions().x + threshold,hero.getDimensions().y);
                break;
        }

        for(RectangleMapObject r: mapObjects.getByType(RectangleMapObject.class)) {
            Rectangle rect = r.getRectangle();
            if(Intersector.overlaps(player,rect))
                return true;
        }

        return false;
    }

    public void checkGhostCollisions() {
        /**
         * Checks ghost's collision with janitor.
         * If a collision occurs, janitor loses life.
         */
        Rectangle player = new Rectangle(hero.getX(),hero.getY(),hero.getDimensions().x,hero.getDimensions().y);
        for(Ghost g: enemies) {
            Rectangle ghost = new Rectangle(g.getX(),g.getY(),g.getWidth(),g.getHeight());
            if(Intersector.overlaps(player,ghost)) {
                hero.takeDamage(game.health);
                return;
            }
        }
    }

    private void checkWin() {
        /**
         * Uses intersector to check janitor's collision with destination.
         * If janitor reaches the destination in last level, he wins. Game will display a success screen.
         * Otherwise he will go to the next screen, decided by the setScreen() method.
         */
        Rectangle player = new Rectangle(hero.getX(),hero.getY(),hero.getDimensions().x,hero.getDimensions().y);
        for(RectangleMapObject r: winLayer.getObjects().getByType(RectangleMapObject.class)) {
            Rectangle rect = r.getRectangle();
            if(Intersector.overlaps(player,rect)) {
                mapData.previousScreenDimensions = new Vector2(Gdx.graphics.getDisplayMode().width, Gdx.graphics.getDisplayMode().height);
                if (getLevelIndex() == 4) {
                    game.success();
                } else {
                    game.setScreen();
                }
            }
        }
    }

    public World getWorld() {
        return world;
    }

    public int getLevelIndex() {
        return this.levelIndex;
    }

    @Override
    public void show() {}
    public void resize(int width, int height) {
        /***
         * Updates viewport and reset camera to center of viewport.
         */
        viewport.update(width, height);
        game.camera.position.set(Constants.VIEWPORT_WIDTH/2, Constants.VIEWPORT_HEIGHT/2, 0);
    }
    public void pause() {}
    public void resume() {}
    public void hide() {}
    public void dispose() {}
}

