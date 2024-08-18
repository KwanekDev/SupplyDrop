package pl.kwanek.supplydrop;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Random;

public final class SupplyDrop extends JavaPlugin implements Listener, CommandExecutor {

    private Random random = new Random();

    @Override
    public void onEnable() {
        // Rejestracja eventów i załadowanie plików
        getServer().getPluginManager().registerEvents(new SupplyDropListener(this), this);
        saveDefaultConfig(); // Tworzy domyślny config, jeśli go nie ma
        loadLanguageFiles(); // Ładowanie plików językowych
        getLogger().info("SupplyDrop Plugin has been enabled!");

        startSupplyDropTask(); // Uruchamia zrzuty

        // Rejestracja komendy
        getCommand("forcesupply").setExecutor(this);
    }

    @Override
    public void onDisable() {
        getLogger().info("SupplyDrop Plugin has been disabled!");
    }

    // Zadanie cykliczne uruchamiające zrzuty w ustalonych odstępach czasu
    public void startSupplyDropTask() {
        long interval = getConfig().getLong("drop.interval");
        new BukkitRunnable() {
            @Override
            public void run() {
                double chance = getConfig().getDouble("drop.chance");
                if (random.nextDouble() * 100 <= chance) {
                    initiateSupplyDrop();
                }
            }
        }.runTaskTimer(this, interval, interval);
    }

    // Inicjowanie zrzutu zaopatrzenia na losowej pozycji
    public void initiateSupplyDrop() {
        World world = Bukkit.getWorld(getConfig().getString("drop.world"));
        int x = random.nextInt((int) world.getWorldBorder().getSize()) - (int) world.getWorldBorder().getSize() / 2;
        int z = random.nextInt((int) world.getWorldBorder().getSize()) - (int) world.getWorldBorder().getSize() / 2;
        int y = world.getHighestBlockYAt(x, z) + getConfig().getInt("drop.heightAboveGround");

        Location dropLocation = new Location(world, x, y, z);
        spawnSupplyDrop(dropLocation);
    }

    // Inicjowanie zrzutu zaopatrzenia nad graczem
    public void initiateSupplyDropOverPlayer(Player player) {
        World world = player.getWorld();
        Location playerLocation = player.getLocation();
        int y = world.getHighestBlockYAt(playerLocation) + getConfig().getInt("drop.heightAboveGround");

        Location dropLocation = new Location(world, playerLocation.getX(), y, playerLocation.getZ());
        spawnSupplyDrop(dropLocation);
    }

    // Spawnowanie zrzutu zaopatrzenia (kowadło) i zamiana na skrzynię po dotknięciu ziemi
    public void spawnSupplyDrop(Location location) {
        World world = location.getWorld();
        FallingBlock fallingAnvil = world.spawnFallingBlock(location, Material.ANVIL.createBlockData());
        fallingAnvil.setDropItem(false); // Upewnia się, że nie zrzuca itemów po upadku

        // Pokazujemy BossBar z koordynatami zrzutu
        showSupplyDropBossBar(location);

        // Wykrycie dotknięcia ziemi i zamiana na skrzynię
        new BukkitRunnable() {
            @Override
            public void run() {
                // Sprawdzamy, czy kowadło dotknęło ziemi
                if (!fallingAnvil.isDead() && fallingAnvil.isOnGround()) {
                    // Pobieramy blok w miejscu, gdzie kowadło dotknęło ziemi
                    Block block = fallingAnvil.getLocation().getBlock();

                    // Zmieniamy ten blok na skrzynię
                    block.setType(Material.CHEST);

                    // Pobieramy stan bloku, który teraz jest skrzynią
                    Chest chest = (Chest) block.getState();

                    // Tworzymy losową zawartość skrzyni
                    Inventory chestInventory = chest.getBlockInventory();
                    List<String> itemsConfig = getConfig().getStringList("drop.items");
                    for (String itemName : itemsConfig) {
                        ItemStack item = new ItemStack(Material.valueOf(itemName), random.nextInt(5) + 1);
                        chestInventory.setItem(random.nextInt(chestInventory.getSize()), item);
                    }

                    this.cancel(); // Zatrzymanie tasku po zamianie na skrzynię
                }
            }
        }.runTaskTimer(this, 0L, 5L);
    }

    // Pokazujemy BossBar z koordynatami zrzutu i animujemy jego progress
    private void showSupplyDropBossBar(Location location) {
        // Pobieramy konfigurację BossBaru
        String title = getConfig().getString("bossbar.title", "Supply Drop: X={x}, Z={z}")
                .replace("{x}", String.valueOf(location.getBlockX()))
                .replace("{z}", String.valueOf(location.getBlockZ()));

        String lang = getConfig().getString("language", "en");
        String message = getLanguageMessage(lang, "drop_coords", title);

        BarColor color = BarColor.valueOf(getConfig().getString("bossbar.color", "BLUE").toUpperCase());
        BarStyle style = BarStyle.valueOf(getConfig().getString("bossbar.style", "SOLID").toUpperCase());

        // Tworzymy BossBar
        BossBar bossBar = Bukkit.createBossBar(message, color, style);
        bossBar.setProgress(1.0); // Początkowy progress

        // Dodajemy BossBar dla wszystkich graczy
        Bukkit.getOnlinePlayers().forEach(bossBar::addPlayer);

        // Animujemy bossbar, zmniejszając progress co 1 sek
        new BukkitRunnable() {
            double progress = 1.0;
            final double decrement = 1.0 / 30.0; // Progress co sekundę na 30 sekund

            @Override
            public void run() {
                progress -= decrement;
                if (progress <= 0) {
                    bossBar.removeAll(); // Usuwamy BossBar po upływie 30 sekund
                    this.cancel();
                } else {
                    bossBar.setProgress(progress);
                }
            }
        }.runTaskTimer(this, 0L, 20L); // Odświeżanie co sekundę
    }

    // Pobieramy wiadomość z pliku językowego na podstawie klucza
    private String getLanguageMessage(String lang, String key, String defaultMessage) {
        String path = "messages." + key;
        if (lang.equalsIgnoreCase("pl")) {
            return getConfig().getString("lang_pl." + path, defaultMessage);
        } else {
            return getConfig().getString("lang_en." + path, defaultMessage);
        }
    }

    // Ładowanie plików językowych z folderu
    private void loadLanguageFiles() {
        saveResource("lang/lang_en.yml", false);
        saveResource("lang/lang_pl.yml", false);
    }

    // Obsługa komendy /forcesupply
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("forcesupply")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                initiateSupplyDropOverPlayer(player);

                // Pobieramy odpowiednią wiadomość z pliku językowego
                String lang = getConfig().getString("language", "en");
                String message = getLanguageMessage(lang, "forcesupply_success", ChatColor.GREEN + "Supply drop spawned above you!");

                player.sendMessage(message);
                return true;
            } else {
                // Pobieramy odpowiednią wiadomość z pliku językowego dla konsoli
                String lang = getConfig().getString("language", "en");
                String message = getLanguageMessage(lang, "forcesupply_console", ChatColor.RED + "Only players can use this command!");

                sender.sendMessage(message);
                return true;
            }
        }
        return false;
    }
}
