package net.samagames.ktp2017;

import net.minecraft.server.v1_10_R1.*;
import net.samagames.api.SamaGamesAPI;
import net.samagames.api.games.Game;
import org.bukkit.*;
import org.bukkit.WorldBorder;
import org.bukkit.craftbukkit.v1_10_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_10_R1.entity.CraftFirework;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import static org.bukkit.Bukkit.getOnlinePlayers;
import static org.bukkit.Bukkit.getWorlds;

public class KTP2017Game extends Game<KTPPlayer> {

    /**
     * There is the internal representation of the Game.
     * @author Vialonyx
     */

    private GamePhase current;
    private BukkitTask remotenessTask;
    private WorldBorder worldBorder;

    private List<KTPArea> avaibleAreas;
    private KTPArea currentlyPlayedArea;
    private KTPArea nextPlayableArea;

    public static KTPArea area1 = new KTPArea(1);
    public static KTPArea area2 = new KTPArea(2);

    public KTP2017Game(KTPMain instance, String gameCodeName, String gameName, String gameDescription, Class<KTPPlayer> gamePlayer) {
        super(gameCodeName, gameName, gameDescription, gamePlayer);

        // Initializing all the things
        this.avaibleAreas = new ArrayList<>();
        this.worldBorder = getWorlds().get(0).getWorldBorder();

        // Registering areas
        this.avaibleAreas.add(KTP2017Game.area1);
        this.avaibleAreas.add(KTP2017Game.area2);

        // Setting current phase to WAIT
        this.current = GamePhase.WAIT;

        // Setting-up default Area
        this.setupArea(area1);

        // Set the next playable Area (Generated automatically and randomly in the future!)
        this.nextPlayableArea = KTP2017Game.area2;

        // Starting remoteness detection (for ALL players)
        this.remotenessTask = KTPMain.getInstance().getServer().getScheduler().runTaskTimer(KTPMain.getInstance(), new Runnable() {

            @Override
            public void run() {

                for(Player p : getOnlinePlayers()){
                    if(p.getLocation().distance(getCurrentlyPlayedArea().getCheckableEntity().getLocation()) >= 20){
                        p.teleport(getCurrentlyPlayedArea().getAreaLocation().clone().add(0.5, 10.00D, 0.5));
                    }
                }

            }

        }, 0L, 100L);

        // Debugging game variables during developement phase
        logDebug();

    }

    @Override
    public void handleLogin(Player player){

        if(this.getCurrentGamePhase() == GamePhase.WAIT || this.getCurrentGamePhase() == GamePhase.AREA_STARTED){
            preparePlayer(player.getUniqueId());

            // -- TEMPORARY FOR DEVELOPEMENT PERIOD --
            if(this.getCurrentlyPlayedArea().getAreaPlayers().size() >= 2){
                SamaGamesAPI.get().getGameManager().getCoherenceMachine().getMessageManager().writeCustomMessage(ChatColor.GREEN + "--- C'est parti ! ---", true);
                this.current = GamePhase.GAME_PHASE2;
            }

        } else {
            player.setGameMode(GameMode.SPECTATOR);
        }

        logDebug();

    }

    public static enum GamePhase {

        WAIT, AREA_STARTED, GAME_PHASE1, GAME_PHASE2, GAME_DONE;

    }

    private void setupArea(KTPArea area){

        // Setting-up WorldBorder
        this.worldBorder.setSize(32);
        this.worldBorder.setCenter(area.getAreaLocation());
        this.worldBorder.setWarningDistance(3);
        this.worldBorder.setDamageAmount(0);

        // Update the current played Area
        this.currentlyPlayedArea = area;

    }

    private void preparePlayer(UUID playerUUID){
        Bukkit.getPlayer(playerUUID).setGameMode(GameMode.SURVIVAL);
        Bukkit.getPlayer(playerUUID).setHealth(0.1);
        Bukkit.getPlayer(playerUUID).setHealthScale(0.1);
        this.getCurrentlyPlayedArea().getAreaPlayers().add(playerUUID);
        Bukkit.getPlayer(playerUUID).teleport(this.getCurrentlyPlayedArea().getAreaLocation());
    }

    public void eliminatePlayer(UUID playerUUID){
        this.getCurrentlyPlayedArea().getAreaPlayers().remove(playerUUID);
        this.setSpectator(Bukkit.getPlayer(playerUUID));
        Bukkit.getPlayer(playerUUID).setGameMode(GameMode.SPECTATOR);
        this.checkWinDetection();
    }

    public void checkWinDetection(){
        if(this.getCurrentlyPlayedArea().getAreaPlayers().size() == 1){

            Player winner = Bukkit.getPlayer(this.getCurrentlyPlayedArea().getAreaPlayers().first());
            FireworkEffect fwWinner_one = FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(Color.GREEN).withFade(Color.SILVER).withFlicker().build();
            FireworkEffect fwWinner_two = FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(Color.WHITE).withFade(Color.YELLOW).withFlicker().build();

            this.current = GamePhase.GAME_DONE;
            SamaGamesAPI.get().getGameManager().getCoherenceMachine().getMessageManager().writeCustomMessage(ChatColor.RED + winner.getDisplayName() + ChatColor.AQUA + " a gagné la partie !", true);
            this.launchfw(winner.getLocation(), fwWinner_one);
            this.launchfw(winner.getLocation(), fwWinner_two);

        }
    }

    public void logDebug(){
        KTPMain.getInstance().getLogger().log(Level.INFO,"------- DEBUG -------");
        KTPMain.getInstance().getLogger().log(Level.INFO,"Current game phase : " + getCurrentGamePhase());
        KTPMain.getInstance().getLogger().log(Level.INFO,this.avaibleAreas.size() + " areas registered. " + avaibleAreas.toString());
        KTPMain.getInstance().getLogger().log(Level.INFO,"-------- END --------");
    }

    private void launchfw(Location location, final FireworkEffect effect) {
        Firework fw = (Firework) location.getWorld().spawnEntity(location, EntityType.FIREWORK);
        FireworkMeta fwm = fw.getFireworkMeta();
        fwm.addEffect(effect);
        fwm.setPower(0);
        fw.setFireworkMeta(fwm);
        ((CraftFirework) fw).getHandle().setInvisible(true);

        KTPMain.getInstance().getServer().getScheduler().runTaskLater(KTPMain.getInstance(), () -> {
            net.minecraft.server.v1_10_R1.World world = (((CraftWorld) location.getWorld()).getHandle());
            EntityFireworks fireworks = ((CraftFirework) fw).getHandle();
            world.broadcastEntityEffect(fireworks, (byte) 17);
            fireworks.die();
        }, 1);
    }

    public GamePhase getCurrentGamePhase(){
        return this.current;
    }

    public KTPArea getCurrentlyPlayedArea(){
        return this.currentlyPlayedArea;
    }

    public KTPArea getNextPlayableArea(){
        return this.nextPlayableArea;
    }

}
