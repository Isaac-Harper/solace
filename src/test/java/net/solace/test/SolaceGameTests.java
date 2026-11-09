package net.solace.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.solace.SolaceData;
import net.solace.SolaceState;

/**
 * In-world GameTests: a real server ticks a real {@link ServerPlayer} (created via the
 * vanilla test helper, which runs the normal join path) and asserts the mod's core
 * behavior. These cover what the pure JUnit tests can't reach: the {@code canAttack}
 * mobs-ignore veto and the per-second {@code ComfortTicker} running over the live player
 * list. Registered via the {@code fabric-gametest} entrypoint; the built-in empty
 * structure is used, so no committed {@code .nbt} is needed.
 *
 * <p>Note: the helper's player is hardwired to report creative and can't be made to take
 * ordinary damage, so damage immunity is covered by the JUnit/logic tests and the
 * "safe state" is exercised here through the mobs-ignore veto instead.
 */
public final class SolaceGameTests {

    private static ServerPlayer mockPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // The mock is creative; clear the invulnerable ability so it reads as a visible
        // enemy target (Player.canBeSeenAsEnemy gates on abilities.invulnerable), otherwise
        // the mob-ignore control below could never see the player as targetable.
        player.getAbilities().invulnerable = false;
        player.onUpdateAbilities();
        player.setHealth(player.getMaxHealth());
        return player;
    }

    /**
     * Mobs ignore an enabled Solace player: the {@code LivingEntity#canAttack} veto makes
     * every mob unable to target her. The control and the assertion share one player and
     * one zombie, so the only variable is Solace being on or off.
     */
    @GameTest
    public void mobsIgnoreSolacePlayer(GameTestHelper helper) {
        // canAttack short-circuits to false in peaceful, which would mask the veto.
        helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
        ServerPlayer player = mockPlayer(helper);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));

        if (!zombie.canAttack(player)) {
            throw helper.assertionException(
                    "control: a zombie should be able to target a non-Solace player");
        }
        SolaceState.set(player, SolaceData.DEFAULT.withEnabled(true));
        if (zombie.canAttack(player)) {
            throw helper.assertionException(
                    "a zombie must not be able to target a Solace player");
        }
        helper.succeed();
    }

    /**
     * No hunger: the Comfort preset keeps a Solace player's food pinned full. The
     * ComfortTicker runs once per second (every 20 server ticks) over the online player
     * list, so start hungry and assert food was restored a couple of cycles later. Vanilla
     * never raises a set food level on its own, so reaching 20 is the mod's doing.
     */
    @GameTest(maxTicks = 80)
    public void hungerStaysFullWhenSolaceEnabled(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        SolaceState.set(player, SolaceData.DEFAULT.withEnabled(true)); // Comfort => no_hunger
        player.getFoodData().setFoodLevel(3);
        helper.runAtTickTime(60, () -> {
            int food = player.getFoodData().getFoodLevel();
            if (food != 20) {
                throw helper.assertionException(
                        "Solace no-hunger should keep food at 20, was " + food);
            }
            helper.succeed();
        });
    }
}
