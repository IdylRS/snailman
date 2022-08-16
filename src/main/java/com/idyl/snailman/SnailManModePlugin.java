package com.idyl.snailman;

import com.google.inject.Provides;

import javax.inject.Inject;

import com.idyl.snailman.pathfinder.CollisionMap;
import com.idyl.snailman.pathfinder.Pathfinder;
import com.idyl.snailman.pathfinder.PathfinderConfig;
import com.idyl.snailman.pathfinder.SplitFlagMap;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.World;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@PluginDescriptor(
	name = "SnailMan Mode"
)
public class SnailManModePlugin extends Plugin
{
	@Inject
	private OverlayManager overlayManager;

	@Inject
	private SnailManModeOverlay snailManModeOverlay;

	@Inject
	private SnailManModeMapOverlay snailManModeMapOverlay;

	@Inject
	private Client client;

	@Inject
	private SnailManModeConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private WorldService worldService;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	private NavigationButton navButton;

	public Pathfinder pathfinder;
	public PathfinderConfig pathfinderConfig;

	private int currentPathIndex;

	private int snailmanIconOffset = -1;

	private boolean onSeasonalWorld;

	private WorldPoint snailWorldPoint;

	private boolean isLoggedIn;
	private boolean isAlive;
	private boolean isDying;
	private WorldPoint deathPoint;

	private WorldPoint transportStart;
	private MenuEntry lastClick;

	private long lastSaveTime;

	private int tickCount = 0;

	private WorldPoint lastPlayerPoint;

	private static final int RECALCULATION_THRESHOLD = 20;
	private static final String ADD_START = "Add start";
	private static final String ADD_END = "Add end";
	private static final String WALK_HERE = "Walk here";
	private static final String TRANSPORT = ColorUtil.wrapWithColorTag("Transport", JagexColors.MENU_TARGET);
	private static final String CONFIG_GROUP = "snailman";
	private static final String CONFIG_KEY_SNAIL_LOC = "snailWorldPoint";
	private static final String CONFIG_KEY_IS_ALIVE = "isAlive";
	private static final WorldPoint DEFAULT_SNAIL_START = new WorldPoint(1181, 3624, 0);

	public static final boolean DEV_MODE = true;

	@Provides
	SnailManModeConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SnailManModeConfig.class);
	}

	@Override
	protected void startUp()
	{
		loadResources();
		isLoggedIn = false;
		onSeasonalWorld = false;
		isAlive = true;
		isDying = false;
		lastSaveTime = Instant.EPOCH.getEpochSecond();
		overlayManager.add(snailManModeOverlay);
		overlayManager.add(snailManModeMapOverlay);

		SnailManModePanel panel = injector.getInstance(SnailManModePanel.class);

		final BufferedImage icon = ImageUtil.loadImageResource(SnailManModePlugin.class, "/snail.png");

		navButton = NavigationButton.builder()
				.panel(panel)
				.tooltip("SnailMan Mode")
				.icon(icon)
				.priority(90)
				.build();

		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(snailManModeOverlay);
		overlayManager.remove(snailManModeMapOverlay);
		clientToolbar.removeNavigation(navButton);
	}

	private void addSnailmanIcon(ChatMessage chatMessage)
	{
		if(!isAlive) return;

		String name = chatMessage.getName();

		boolean isLocalPlayer = Text.standardize(name).equalsIgnoreCase(Text.standardize(client.getLocalPlayer().getName()));

		if(!isLocalPlayer) return;

		chatMessage.getMessageNode().setName(getImgTag(snailmanIconOffset)+Text.removeTags(name));
	}

	public WorldPoint getSnailWorldPoint() {
		return snailWorldPoint;
	}

	private WorldPoint getSavedSnailWorldPoint() {
		if(this.configManager.getRSProfileKey() == null) return null;

		final WorldPoint point = configManager.getRSProfileConfiguration(CONFIG_GROUP, CONFIG_KEY_SNAIL_LOC, WorldPoint.class);

		if(point == null) {
			return DEFAULT_SNAIL_START;
		}

		return point;
	}

	public void setSnailWorldPoint(WorldPoint point) {
		snailWorldPoint = point;
	}

	private void saveSnailWorldPoint() {
		if(this.configManager.getRSProfileKey() == null) return;

		this.configManager.setRSProfileConfiguration(CONFIG_GROUP, CONFIG_KEY_SNAIL_LOC, snailWorldPoint);
	}

	private void saveData() {
		if(this.configManager.getRSProfileKey() == null) return;

		saveSnailWorldPoint();
		configManager.setRSProfileConfiguration(CONFIG_GROUP, CONFIG_KEY_IS_ALIVE, isAlive);

		lastSaveTime = Instant.EPOCH.getEpochSecond();
	}

	public void reset() {
		setSnailWorldPoint(DEFAULT_SNAIL_START);
		pathfinder = null;
		isAlive = true;
		isDying = false;
		deathPoint = null;
		saveData();
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event) {
		if (client.isKeyPressed(KeyCode.KC_SHIFT) && event.getOption().equals(WALK_HERE) && DEV_MODE) {
			addMenuEntry(event, ADD_START);
			addMenuEntry(event, ADD_END);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if(gameStateChanged.getGameState() == GameState.LOGGED_IN && !isLoggedIn) {
			final WorldPoint point = getSavedSnailWorldPoint();
			String savedAlive = configManager.getRSProfileConfiguration(CONFIG_GROUP, CONFIG_KEY_IS_ALIVE);
			setSnailWorldPoint(point);
			currentPathIndex = 1;
			isAlive = savedAlive == null || Boolean.parseBoolean(savedAlive);
			isLoggedIn = true;
			onSeasonalWorld = isSeasonalWorld(client.getWorld());
		}
		else if(gameStateChanged.getGameState() == GameState.LOGIN_SCREEN && isLoggedIn){
			isLoggedIn = false;
			saveData();
			pathfinder = null;
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick) {
		if(!isLoggedIn || config.pauseSnail() || isSeasonalWorld(client.getWorld())) return;

		tickCount++;

		WorldPoint playerPoint = client.getLocalPlayer().getWorldLocation();

		long distanceToSnail = playerPoint.distanceTo2D(snailWorldPoint);
		boolean snailShouldMove = tickCount % config.moveSpeed() == 0 || (distanceToSnail <= RECALCULATION_THRESHOLD && config.speedBoost());

		if(lastPlayerPoint == null) lastPlayerPoint = playerPoint;

		if(!snailShouldMove) return;

		if(pathfinder == null) {
			calculatePath(snailWorldPoint, playerPoint, false, false);
			return;
		}

		if(currentPathIndex < pathfinder.getPath().size() && pathfinder.isComplete) {
			int index = Math.max(pathfinder.getPath().size() - 1 - this.currentPathIndex, 0);
			WorldPoint target = pathfinder.getPath().get(index);
			setSnailWorldPoint(target);
			currentPathIndex++;
		}

		if(checkTouching()) {
			final ChatMessageBuilder message = new ChatMessageBuilder()
						.append(ChatColorType.HIGHLIGHT)
						.append("You have been touched by the snail. You are dead.")
						.append(ChatColorType.NORMAL);

			if(isAlive) {
				isDying = true;
				deathPoint = playerPoint;
				chatMessageManager.queue(QueuedMessage.builder()
						.type(ChatMessageType.GAMEMESSAGE)
						.runeLiteFormattedMessage(message.build())
						.build());

				client.playSoundEffect(SoundEffectID.ATTACK_HIT);

				client.getLocalPlayer().setAnimation(AnimationID.DEATH);
				client.getLocalPlayer().setAnimationFrame(0);

				isAlive = false;
				clientThread.invoke(() -> client.runScript(ScriptID.CHAT_PROMPT_INIT));
				saveData();
			}
		}
		recalculatePath();

		lastPlayerPoint = playerPoint;

		if(Instant.EPOCH.getEpochSecond() - lastSaveTime >= 60 && isLoggedIn) {
			saveData();
		}

		if(isDying) {
			if(!deathPoint.equals(playerPoint)) {
				isDying = false;
				client.getLocalPlayer().setAnimation(AnimationID.IDLE);
			}
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		if (client.getGameState() != GameState.LOADING && client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		if(client.getLocalPlayer().getName() == null) return;

		String name = Text.removeTags(chatMessage.getName());
		boolean isSelf = client.getLocalPlayer().getName().equalsIgnoreCase(name);

		switch (chatMessage.getType())
		{
			case PRIVATECHAT:
			case MODPRIVATECHAT:
			case FRIENDSCHAT:
			case CLAN_CHAT:
				if (!onSeasonalWorld && isSelf)
				{
					addSnailmanIcon(chatMessage);
				}
				break;
			case PUBLICCHAT:
			case MODCHAT:
				if (isSelf)
				{
					addSnailmanIcon(chatMessage);
				}
				break;
		}
	}

	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		if (!event.getEventName().equals("setChatboxInput"))
		{
			return;
		}

		updateChatbox();
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		updateChatbox();
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (event.getActor() instanceof Player)
		{
			final Player player = (Player) event.getActor();

			if(isDying) {
				isDying = false;
				client.getLocalPlayer().setAnimation(AnimationID.IDLE);
			}
		}
	}

	private void addMenuEntry(MenuEntryAdded event, String option) {
		client.createMenuEntry(1)
				.setOption(option)
				.setTarget(SnailManModePlugin.TRANSPORT)
				.setParam0(event.getActionParam0())
				.setParam1(event.getActionParam1())
				.setIdentifier(event.getIdentifier())
				.setType(MenuAction.RUNELITE)
				.onClick(this::onMenuOptionClicked);
	}

	private void onMenuOptionClicked(MenuEntry entry) {
		Player localPlayer = client.getLocalPlayer();

		WorldPoint currentLocation = localPlayer.getWorldLocation();
		if (entry.getOption().equals(ADD_START) && entry.getTarget().equals(TRANSPORT)) {
			transportStart = currentLocation;
		}

		if (entry.getOption().equals(ADD_END) && entry.getTarget().equals(TRANSPORT)) {
			WorldPoint transportEnd = client.getLocalPlayer().getWorldLocation();
			String transportText = transportStart.getX() + " " + transportStart.getY() + " " + transportStart.getPlane() + "\t" +
					currentLocation.getX() + " " + currentLocation.getY() + " " + currentLocation.getPlane() + "\t" +
					lastClick.getOption() + " " + Text.removeTags(lastClick.getTarget()) + " " + lastClick.getIdentifier();
			Pathfinder.writeTransportToFile(transportText);
			Transport transport = new Transport(transportStart, transportEnd);
			pathfinderConfig.getTransports().computeIfAbsent(transportStart, k -> new ArrayList<>()).add(transport);
		}

		if (entry.getType() != MenuAction.WALK) {
			lastClick = entry;
		}
	}

	private void updateChatbox()
	{
		if(!isAlive) {
			return;
		}

		Widget chatboxTypedText = client.getWidget(WidgetInfo.CHATBOX_INPUT);

		if (snailmanIconOffset == -1)
		{
			return;
		}

		if (chatboxTypedText == null || chatboxTypedText.isHidden())
		{
			return;
		}

		String[] chatbox = chatboxTypedText.getText().split(":", 2);
		String rsn = Objects.requireNonNull(client.getLocalPlayer()).getName();

		chatboxTypedText.setText(getImgTag(snailmanIconOffset) + Text.removeTags(rsn) + ":" + chatbox[1]);
	}

	private static String getImgTag(int iconIndex)
	{
		return "<img=" + iconIndex + ">";
	}

	private boolean isSeasonalWorld(int worldNumber)
	{
		WorldResult worlds = worldService.getWorlds();
		if (worlds == null)
		{
			return false;
		}

		World world = worlds.findWorld(worldNumber);
		return world != null && world.getTypes().contains(WorldType.SEASONAL);
	}

	private void recalculatePath() {
		if(!isLoggedIn) return;

		if(client.isInInstancedRegion()) {
			pathfinder = null;
			return;
		}

		WorldPoint playerPoint = client.getLocalPlayer().getWorldLocation();
		final int distanceFromPlayer = snailWorldPoint.distanceTo2D(playerPoint);

		boolean forceRecalc = lastPlayerPoint.getPlane() != playerPoint.getPlane();

		if(distanceFromPlayer < RECALCULATION_THRESHOLD) {
			if(pathfinder.getStart().distanceTo2D(playerPoint) > 0) {
				calculatePath(snailWorldPoint, playerPoint, true, false);
				this.currentPathIndex = 1;
			}
		}
		else {
			// Limit number of recalculations done during player movement
			if(pathfinder.getStart().distanceTo2D(playerPoint) >= RECALCULATION_THRESHOLD || forceRecalc) {
				boolean useExistingPath = lastPlayerPoint.distanceTo(playerPoint) < 100;
				calculatePath(snailWorldPoint, playerPoint, forceRecalc, useExistingPath);
				this.currentPathIndex = 1;
			}
		}
	}

	private boolean checkTouching() {
		WorldPoint playerPoint = client.getLocalPlayer().getWorldLocation();
		final int distanceFromPlayer = snailWorldPoint.distanceTo2D(playerPoint);

		return distanceFromPlayer <= 0;
	}

	private void calculatePath(WorldPoint start, WorldPoint end, boolean force, boolean useExisting) {
		if(pathfinder != null && !pathfinder.isDone() && !force) return;

		if(client.isInInstancedRegion()) return;

		List<WorldPoint> existingPath = useExisting ? pathfinder.getPath() : null;

		pathfinder = new Pathfinder(pathfinderConfig, end, start, existingPath);

	}

	public Point mapWorldPointToGraphicsPoint(WorldPoint worldPoint)
	{
		RenderOverview ro = client.getRenderOverview();

		float pixelsPerTile = ro.getWorldMapZoom();

		Widget map = client.getWidget(WidgetInfo.WORLD_MAP_VIEW);
		if (map != null) {
			Rectangle worldMapRect = map.getBounds();

			int widthInTiles = (int) Math.ceil(worldMapRect.getWidth() / pixelsPerTile);
			int heightInTiles = (int) Math.ceil(worldMapRect.getHeight() / pixelsPerTile);

			Point worldMapPosition = ro.getWorldMapPosition();

			int yTileMax = worldMapPosition.getY() - heightInTiles / 2;
			int yTileOffset = (yTileMax - worldPoint.getY() - 1) * -1;
			int xTileOffset = worldPoint.getX() + widthInTiles / 2 - worldMapPosition.getX();

			int xGraphDiff = ((int) (xTileOffset * pixelsPerTile));
			int yGraphDiff = (int) (yTileOffset * pixelsPerTile);

			yGraphDiff -= pixelsPerTile - Math.ceil(pixelsPerTile / 2);
			xGraphDiff += pixelsPerTile - Math.ceil(pixelsPerTile / 2);

			yGraphDiff = worldMapRect.height - yGraphDiff;
			yGraphDiff += (int) worldMapRect.getY();
			xGraphDiff += (int) worldMapRect.getX();

			return new Point(xGraphDiff, yGraphDiff);
		}
		return null;
	}

	private void loadResources()
	{
		Map<SplitFlagMap.Position, byte[]> compressedRegions = new HashMap<>();
		HashMap<WorldPoint, List<Transport>> transports = new HashMap<>();

		try (ZipInputStream in = new ZipInputStream(SnailManModePlugin.class.getResourceAsStream("/collision-map.zip"))) {
			ZipEntry entry;
			while ((entry = in.getNextEntry()) != null) {
				String[] n = entry.getName().split("_");

				compressedRegions.put(
						new SplitFlagMap.Position(Integer.parseInt(n[0]), Integer.parseInt(n[1])),
						Util.readAllBytes(in)
				);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		try {
			String s = new String(Util.readAllBytes(SnailManModePlugin.class.getResourceAsStream("/transports.txt")), StandardCharsets.UTF_8);
			Scanner scanner = new Scanner(s);
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();

				if (line.startsWith("#") || line.isEmpty()) {
					continue;
				}

				log.info(line);

				Transport transport = new Transport(line);
				WorldPoint origin = transport.getOrigin();
				transports.computeIfAbsent(origin, k -> new ArrayList<>()).add(transport);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		pathfinderConfig = new PathfinderConfig(new CollisionMap(64, compressedRegions), transports, client);

		final IndexedSprite[] modIcons = client.getModIcons();

		if (snailmanIconOffset != -1 || modIcons == null)
		{
			return;
		}

		BufferedImage image = ImageUtil.loadImageResource(getClass(),"/helm.png");
		IndexedSprite indexedSprite = ImageUtil.getImageIndexedSprite(image, client);
		snailmanIconOffset = modIcons.length;

		final IndexedSprite[] newModIcons = Arrays.copyOf(modIcons, modIcons.length + 1);
		newModIcons[newModIcons.length - 1] = indexedSprite;

		client.setModIcons(newModIcons);
	}
}
