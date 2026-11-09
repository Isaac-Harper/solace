package net.solace.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.solace.config.SolaceConfig;

/**
 * Explosion base protection (M8) in a live world: each test builds a dirt floor on an
 * obsidian base (so fire always has solid ground once the dirt is cratered), sets the
 * protection mode, detonates one explosion just above the floor and checks the floor, an
 * exposed pig and any fire straight away. Everything runs inside a single call, so the
 * shared config is set and restored before any other test can observe it.
 */
public final class ExplosionProtectionGameTests {

    private static final int FLOOR_Y = 2;
    private static final int FLOOR_MIN = 2;
    private static final int FLOOR_MAX = 6;
    private static final Vec3 BLAST_CENTER = new Vec3(4.5, FLOOR_Y + 1.5, 4.5);
    private static final float CREEPER_POWER = 3.0F;
    private static final float TNT_POWER = 4.0F;

    private static void buildFloor(GameTestHelper helper) {
        for (int x = FLOOR_MIN; x <= FLOOR_MAX; x++) {
            for (int z = FLOOR_MIN; z <= FLOOR_MAX; z++) {
                helper.setBlock(new BlockPos(x, FLOOR_Y - 1, z), Blocks.OBSIDIAN);
                helper.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.DIRT);
            }
        }
    }

    private static int floorBlocksLeft(GameTestHelper helper) {
        int left = 0;
        for (int x = FLOOR_MIN; x <= FLOOR_MAX; x++) {
            for (int z = FLOOR_MIN; z <= FLOOR_MAX; z++) {
                if (helper.getBlockState(new BlockPos(x, FLOOR_Y, z)).is(Blocks.DIRT)) {
                    left++;
                }
            }
        }
        return left;
    }

    private static int fireBlocks(GameTestHelper helper) {
        int fires = 0;
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, FLOOR_Y - 1, 0), new BlockPos(7, 7, 7))) {
            if (helper.getBlockState(pos).is(Blocks.FIRE)) {
                fires++;
            }
        }
        return fires;
    }

    private static void assertFloorIntact(GameTestHelper helper) {
        int total = (FLOOR_MAX - FLOOR_MIN + 1) * (FLOOR_MAX - FLOOR_MIN + 1);
        int left = floorBlocksLeft(helper);
        if (left != total) {
            throw helper.assertionException("protected explosion broke " + (total - left) + " floor blocks");
        }
    }

    private static void assertFloorBroken(GameTestHelper helper) {
        int total = (FLOOR_MAX - FLOOR_MIN + 1) * (FLOOR_MAX - FLOOR_MIN + 1);
        if (floorBlocksLeft(helper) == total) {
            throw helper.assertionException("unprotected explosion should break floor blocks");
        }
    }

    private static Pig spawnPig(GameTestHelper helper) {
        return helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(5, FLOOR_Y + 1, 4));
    }

    private static void assertDamaged(GameTestHelper helper, Pig pig) {
        if (pig.getHealth() >= pig.getMaxHealth()) {
            throw helper.assertionException("explosion should still damage nearby entities");
        }
    }

    private static void explode(GameTestHelper helper, Entity source, float power, boolean fire,
                                Level.ExplosionInteraction interaction) {
        Vec3 center = helper.absoluteVec(BLAST_CENTER);
        helper.getLevel().explode(source, center.x, center.y, center.z, power, fire, interaction);
    }

    private static SolaceConfig.Home homeAt(GameTestHelper helper, Vec3 relative) {
        SolaceConfig.Home home = new SolaceConfig.Home();
        Vec3 absolute = helper.absoluteVec(relative);
        home.dimension = helper.getLevel().dimension().identifier().toString();
        home.x = absolute.x;
        home.y = absolute.y;
        home.z = absolute.z;
        return home;
    }

    private static void withProtection(String mode, SolaceConfig.Home home, Runnable test) {
        SolaceConfig config = SolaceConfig.get();
        String previousMode = config.baseProtection.mode;
        SolaceConfig.Home previousHome = config.home;
        config.baseProtection.mode = mode;
        config.home = home;
        try {
            test.run();
        } finally {
            config.baseProtection.mode = previousMode;
            config.home = previousHome;
        }
    }

    private static void creeperBlast(GameTestHelper helper) {
        Entity creeper = helper.spawnWithNoFreeWill(EntityTypes.CREEPER, new BlockPos(4, FLOOR_Y + 1, 4));
        explode(helper, creeper, CREEPER_POWER, false, Level.ExplosionInteraction.MOB);
        creeper.discard();
    }

    private static void tntBlast(GameTestHelper helper) {
        Entity tnt = helper.spawn(EntityTypes.TNT, new BlockPos(4, FLOOR_Y + 1, 4));
        explode(helper, tnt, TNT_POWER, false, Level.ExplosionInteraction.TNT);
        tnt.discard();
    }

    @GameTest
    public void creeperKeepsBlocksButHurtsEntitiesUnderHostileExplosions(GameTestHelper helper) {
        buildFloor(helper);
        Pig pig = spawnPig(helper);
        withProtection(SolaceConfig.BaseProtection.HOSTILE_EXPLOSIONS, null, () -> creeperBlast(helper));
        assertFloorIntact(helper);
        assertDamaged(helper, pig);
        helper.succeed();
    }

    @GameTest
    public void creeperBreaksBlocksWhenProtectionOff(GameTestHelper helper) {
        buildFloor(helper);
        Pig pig = spawnPig(helper);
        withProtection(SolaceConfig.BaseProtection.OFF, null, () -> creeperBlast(helper));
        assertFloorBroken(helper);
        assertDamaged(helper, pig);
        helper.succeed();
    }

    @GameTest
    public void tntBreaksBlocksUnderHostileExplosions(GameTestHelper helper) {
        buildFloor(helper);
        withProtection(SolaceConfig.BaseProtection.HOSTILE_EXPLOSIONS, null, () -> tntBlast(helper));
        assertFloorBroken(helper);
        helper.succeed();
    }

    @GameTest
    public void tntKeepsBlocksButHurtsEntitiesUnderAllExplosions(GameTestHelper helper) {
        buildFloor(helper);
        Pig pig = spawnPig(helper);
        withProtection(SolaceConfig.BaseProtection.ALL_EXPLOSIONS, null, () -> tntBlast(helper));
        assertFloorIntact(helper);
        assertDamaged(helper, pig);
        helper.succeed();
    }

    @GameTest
    public void homeRegionProtectsAnyExplosionInsideRadius(GameTestHelper helper) {
        buildFloor(helper);
        SolaceConfig.Home home = homeAt(helper, BLAST_CENTER);
        withProtection(SolaceConfig.BaseProtection.HOME_REGION, home, () -> tntBlast(helper));
        assertFloorIntact(helper);
        helper.succeed();
    }

    @GameTest
    public void homeRegionLeavesCreepersOutsideRadiusUnprotected(GameTestHelper helper) {
        buildFloor(helper);
        double outside = SolaceConfig.get().baseProtection.homeRadius + 16;
        SolaceConfig.Home home = homeAt(helper, BLAST_CENTER.add(outside, 0, 0));
        withProtection(SolaceConfig.BaseProtection.HOME_REGION, home, () -> creeperBlast(helper));
        assertFloorBroken(helper);
        helper.succeed();
    }

    @GameTest
    public void ghastFireballStartsNoFireUnderHostileExplosions(GameTestHelper helper) {
        buildFloor(helper);
        Ghast ghast = helper.spawnWithNoFreeWill(EntityTypes.GHAST, new BlockPos(1, 4, 1));
        LargeFireball fireball = new LargeFireball(helper.getLevel(), ghast, Vec3.ZERO, 1);
        withProtection(SolaceConfig.BaseProtection.HOSTILE_EXPLOSIONS, null,
                () -> explode(helper, fireball, CREEPER_POWER, true, Level.ExplosionInteraction.MOB));
        assertFloorIntact(helper);
        int fires = fireBlocks(helper);
        if (fires != 0) {
            throw helper.assertionException("protected ghast fireball started " + fires + " fires");
        }
        helper.succeed();
    }

    @GameTest
    public void ghastFireballStartsFireWhenProtectionOff(GameTestHelper helper) {
        buildFloor(helper);
        Ghast ghast = helper.spawnWithNoFreeWill(EntityTypes.GHAST, new BlockPos(1, 4, 1));
        LargeFireball fireball = new LargeFireball(helper.getLevel(), ghast, Vec3.ZERO, 1);
        withProtection(SolaceConfig.BaseProtection.OFF, null,
                () -> explode(helper, fireball, CREEPER_POWER, true, Level.ExplosionInteraction.MOB));
        if (fireBlocks(helper) == 0) {
            throw helper.assertionException("unprotected ghast fireball should start fires");
        }
        helper.succeed();
    }

    @GameTest
    public void windChargeStillFlipsLeversUnderAllExplosions(GameTestHelper helper) {
        buildFloor(helper);
        BlockPos leverPos = new BlockPos(4, FLOOR_Y + 1, 5);
        BlockState lever = Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR);
        helper.setBlock(leverPos, lever);
        withProtection(SolaceConfig.BaseProtection.ALL_EXPLOSIONS, null,
                () -> explode(helper, null, 1.2F, false, Level.ExplosionInteraction.TRIGGER));
        helper.assertBlockProperty(leverPos, LeverBlock.POWERED, true);
        assertFloorIntact(helper);
        helper.succeed();
    }
}
