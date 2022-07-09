package com.idyl.snailman;

import com.google.inject.Provides;
import javax.inject.Inject;

import com.idyl.snailman.pathfinder.CollisionMap;
import com.idyl.snailman.pathfinder.Pathfinder;
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

	private SnailManModePanel panel;

	public Pathfinder pathfinder;
	public Pathfinder.Path currentPath;

	private int currentPathIndex;

	private int snailmanIconOffset = -1;

	private boolean onSeasonalWorld;

	private WorldPoint snailWorldPoint;

	private boolean isLoggedIn;
	private boolean isAlive;

	private WorldPoint transportStart;
	private MenuEntry lastClick;

	private NavigationButton navButton;

	private static final int RECALCULATION_THRESHOLD = 20;
	private static final String ADD_START = "Add start";
	private static final String ADD_END = "Add end";
	private static final String WALK_HERE = "Walk here";
	private static final String TRANSPORT = ColorUtil.wrapWithColorTag("Transport", JagexColors.MENU_TARGET);
	private static final String CONFIG_GROUP = "snailman";
	private static final String CONFIG_KEY_SNAIL_LOC = "snailWorldPoint";
	private static final String CONFIG_KEY_IS_ALIVE = "isAlive";
	private static final WorldPoint DEFAULT_SNAIL_START = new WorldPoint(1181, 3624, 0);
	public static final boolean DEV_MODE = false;

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
		overlayManager.add(snailManModeOverlay);
		overlayManager.add(snailManModeMapOverlay);

		panel = injector.getInstance(SnailManModePanel.class);

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

		log.info("Getting from profile: "+this.configManager.getRSProfileKey()+", value: "+point);

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

		log.info("Saving to profile: "+this.configManager.getRSProfileKey()+", value: "+this.snailWorldPoint);

		this.configManager.setRSProfileConfiguration(CONFIG_GROUP, CONFIG_KEY_SNAIL_LOC, snailWorldPoint);
	}

	private void saveData() {
		if(this.configManager.getRSProfileKey() == null) return;

		saveSnailWorldPoint();
		configManager.setRSProfileConfiguration(CONFIG_GROUP, CONFIG_KEY_IS_ALIVE, isAlive);
	}

	public void reset() {
		setSnailWorldPoint(DEFAULT_SNAIL_START);
		currentPath = null;
		isAlive = true;
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
			isAlive = savedAlive == null ? true : Boolean.parseBoolean(savedAlive);
			isLoggedIn = true;
			onSeasonalWorld = isSeasonalWorld(client.getWorld());
		}
		else if(gameStateChanged.getGameState() == GameState.LOGIN_SCREEN && isLoggedIn){
			isLoggedIn = false;
			saveData();
			currentPath = null;
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick) {
		if(!isLoggedIn) return;

		WorldPoint playerPoint = client.getLocalPlayer().getWorldLocation();

		if(currentPath == null) {
			currentPath = calculatePath(snailWorldPoint, playerPoint);
		}

		if(currentPathIndex < currentPath.getPath().size()) {
			WorldPoint target = currentPath.getPath().get(currentPathIndex);
			setSnailWorldPoint(target);
			currentPathIndex++;
		}

		if(checkTouching()) {
			final ChatMessageBuilder message = new ChatMessageBuilder()
						.append(ChatColorType.HIGHLIGHT)
						.append("You have been touched by the snail. You are dead.")
						.append(ChatColorType.NORMAL);

			if(isAlive) {
				chatMessageManager.queue(QueuedMessage.builder()
						.type(ChatMessageType.GAMEMESSAGE)
						.runeLiteFormattedMessage(message.build())
						.build());

				client.playSoundEffect(SoundEffectID.ATTACK_HIT);

				isAlive = false;
				clientThread.invoke(() -> client.runScript(ScriptID.CHAT_PROMPT_INIT));
				saveData();
			}
		}
		moveSnailTowardsPlayer();
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
				// Note this is unable to change icon on PMs if they are not a friend or in friends chat
			case FRIENDSCHAT:
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
			String transport = transportStart.getX() + " " + transportStart.getY() + " " + transportStart.getPlane() + " " +
					currentLocation.getX() + " " + currentLocation.getY() + " " + currentLocation.getPlane() + " " +
					lastClick.getOption() + " " + Text.removeTags(lastClick.getTarget()) + " " + lastClick.getIdentifier();
			System.out.println(transport);
			pathfinder.transports.computeIfAbsent(transportStart, k -> new ArrayList<>()).add(currentLocation);
			pathfinder.writeTransportToFile(transport);
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

	private void moveSnailTowardsPlayer() {
		if(!isLoggedIn) return;

		WorldPoint playerPoint = client.getLocalPlayer().getWorldLocation();
		final int distanceFromPlayer = snailWorldPoint.distanceTo2D(playerPoint);

		if(distanceFromPlayer < RECALCULATION_THRESHOLD) {
			if(currentPath.getTarget().distanceTo2D(playerPoint) > 0) {
				currentPath = calculatePath(snailWorldPoint, playerPoint);
				this.currentPathIndex = 1;
			}
		}
		else {
			// Limit number of recalculations done during player movement
			if(currentPath.getTarget().distanceTo2D(playerPoint) > 50) {
				if(client.isInInstancedRegion()) return;

				currentPath = calculatePath(snailWorldPoint, playerPoint);
				this.currentPathIndex = 1;
			}
		}
	}

	private boolean checkTouching() {
		WorldPoint playerPoint = client.getLocalPlayer().getWorldLocation();
		final int distanceFromPlayer = snailWorldPoint.distanceTo2D(playerPoint);

		return distanceFromPlayer <= 0;
	}

	private Pathfinder.Path calculatePath(WorldPoint start, WorldPoint end) {
		if(currentPath != null && currentPath.loading) return currentPath;
		if(currentPath != null) currentPath.stopThread();

		return pathfinder.new Path(start, end, false);
	}

	public WorldPoint calculateMapPoint(Point point) {
		float zoom = client.getRenderOverview().getWorldMapZoom();
		RenderOverview renderOverview = client.getRenderOverview();
		final WorldPoint mapPoint = new WorldPoint(renderOverview.getWorldMapPosition().getX(), renderOverview.getWorldMapPosition().getY(), 0);
		final Point middle = mapWorldPointToGraphicsPoint(mapPoint);

		if (point == null || middle == null) {
			return null;
		}

		final int dx = (int) ((point.getX() - middle.getX()) / zoom);
		final int dy = (int) ((-(point.getY() - middle.getY())) / zoom);

		return mapPoint.dx(dx).dy(dy);
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
		HashMap<WorldPoint, List<WorldPoint>> transports = new HashMap<>();

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

				String[] l = line.split(" ");
				WorldPoint a = new WorldPoint(Integer.parseInt(l[0]), Integer.parseInt(l[1]), Integer.parseInt(l[2]));
				WorldPoint b = new WorldPoint(Integer.parseInt(l[3]), Integer.parseInt(l[4]), Integer.parseInt(l[5]));
				transports.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		CollisionMap map = new CollisionMap(64, compressedRegions);
		pathfinder = new Pathfinder(map, transports);

		final IndexedSprite[] modIcons = client.getModIcons();

		if (snailmanIconOffset != -1 || modIcons == null)
		{
			return;
		}

		BufferedImage image = ImageUtil.getResourceStreamFromClass(getClass(), "/helm.png");
		IndexedSprite indexedSprite = ImageUtil.getImageIndexedSprite(image, client);

		snailmanIconOffset = modIcons.length;

		final IndexedSprite[] newModIcons = Arrays.copyOf(modIcons, modIcons.length + 1);
		newModIcons[newModIcons.length - 1] = indexedSprite;

		client.setModIcons(newModIcons);
	}
}
