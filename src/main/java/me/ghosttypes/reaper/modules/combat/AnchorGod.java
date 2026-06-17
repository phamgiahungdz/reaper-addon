package me.ghosttypes.reaper.modules.combat;

import me.ghosttypes.reaper.events.DeathEvent;
import me.ghosttypes.reaper.modules.ML;
import me.ghosttypes.reaper.util.misc.ReaperModule;
import me.ghosttypes.reaper.util.misc.MathUtil;
import me.ghosttypes.reaper.util.network.PacketManager;
import me.ghosttypes.reaper.util.player.Interactions;
import me.ghosttypes.reaper.util.render.Renderers;
import me.ghosttypes.reaper.util.world.BlockHelper;
import me.ghosttypes.reaper.util.world.CombatHelper;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;

import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;

import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.*;

import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;

import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;


public class AnchorGod extends ReaperModule {


    private class AnchorPlacement {

        private BlockPos pos;
        private Vec3d vec;
        private BlockHitResult hit;


        public AnchorPlacement() {}


        public AnchorPlacement(BlockPos pos) {
            set(pos);
        }


        public void set(BlockPos pos) {

            this.pos = pos;
            this.vec = BlockHelper.vec3d(pos);

            this.hit =
                new BlockHitResult(
                    BlockHelper.bestHitPos(pos),
                    Direction.UP,
                    pos,
                    true
                );
        }


        public BlockPos getPos() {
            return pos;
        }


        public BlockHitResult getHit() {
            return hit;
        }


        public double selfDamage() {

            if (mc.player == null)
                return 999;

            return DamageUtils.bedDamage(
                mc.player,
                vec
            );
        }


        public double targetDamage() {

            if (target == null)
                return 0;

            return DamageUtils.bedDamage(
                target,
                vec
            );
        }
    }



    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup targetGroup = settings.createGroup("Target");
    private final SettingGroup renderGroup = settings.createGroup("Render");
    private final SettingGroup miscGroup = settings.createGroup("Misc");



    public final Setting<Double> range =
        targetGroup.add(
            new DoubleSetting.Builder()
                .name("range")
                .defaultValue(7)
                .sliderRange(1,30)
                .build()
        );


    public final Setting<Integer> xRadius =
        targetGroup.add(
            new IntSetting.Builder()
                .name("x-radius")
                .defaultValue(5)
                .sliderRange(1,9)
                .build()
        );


    public final Setting<Integer> yRadius =
        targetGroup.add(
            new IntSetting.Builder()
                .name("y-radius")
                .defaultValue(4)
                .sliderRange(1,6)
                .build()
        );


    public final Setting<Double> placeRange =
        targetGroup.add(
            new DoubleSetting.Builder()
                .name("place-range")
                .defaultValue(4.5)
                .sliderRange(1,10)
                .build()
        );


    public final Setting<Double> minDamage =
        targetGroup.add(
            new DoubleSetting.Builder()
                .name("min-damage")
                .defaultValue(5)
                .range(0,36)
                .build()
        );


    public final Setting<Double> maxSelf =
        targetGroup.add(
            new DoubleSetting.Builder()
                .name("max-self")
                .defaultValue(4)
                .range(0,36)
                .build()
        );



    public final Setting<Boolean> rotate =
        general.add(
            new BoolSetting.Builder()
                .name("rotate")
                .defaultValue(false)
                .build()
        );


    public final Setting<Boolean> packetPlace =
        general.add(
            new BoolSetting.Builder()
                .name("packet-place")
                .defaultValue(false)
                .build()
        );


    public final Setting<Integer> placeDelay =
        general.add(
            new IntSetting.Builder()
                .name("place-delay")
                .defaultValue(4)
                .sliderRange(0,20)
                .build()
        );


    public final Setting<Integer> breakDelay =
        general.add(
            new IntSetting.Builder()
                .name("break-delay")
                .defaultValue(4)
                .sliderRange(0,20)
                .build()
        );


    public final Setting<Boolean> antiSuicide =
        miscGroup.add(
            new BoolSetting.Builder()
                .name("anti-suicide")
                .defaultValue(true)
                .build()
        );


    public final Setting<Boolean> antiDesync =
        miscGroup.add(
            new BoolSetting.Builder()
                .name("anti-desync")
                .defaultValue(true)
                .build()
        );


    public final Setting<Boolean> fastCalc =
        miscGroup.add(
            new BoolSetting.Builder()
                .name("fast-calc")
                .defaultValue(true)
                .build()
        );



    private PlayerEntity target;

    private AnchorPlacement placePos;
    private AnchorPlacement breakPos;


    private Renderers.SimpleAnchorRender anchorRender;
    private Renderers.SimpleAnchorRender breakRender;


    private int placeTimer;
    private int breakTimer;


    // stability additions

    private ArrayList<BlockPos> cachedSphere =
        new ArrayList<>();

    private BlockPos lastTargetPos;

    private long lastAction;



    public AnchorGod() {

        super(
            ML.R,
            "anchor-god",
            "stable anchor aura"
        );
    }
    @Override
public void onActivate() {

    target = null;

    placePos = null;
    breakPos = null;

    anchorRender = null;
    breakRender = null;

    placeTimer = 0;
    breakTimer = breakDelay.get();

    cachedSphere.clear();
    lastTargetPos = null;

    lastAction = 0;
}



@EventHandler
private void onRender3D(Render3DEvent event) {

    if (anchorRender != null &&
        anchorRender.getPos() != null) {

        event.renderer.box(
            anchorRender.getPos(),
            anchorRender.getSideColor(),
            anchorRender.getLineColor(),
            ShapeMode.Both,
            0
        );
    }


    if (breakRender != null &&
        breakRender.getPos() != null) {

        event.renderer.box(
            breakRender.getPos(),
            breakRender.getSideColor(),
            breakRender.getLineColor(),
            ShapeMode.Both,
            0
        );
    }
}



@EventHandler
private void onRender2D(Render2DEvent event) {

    if (anchorRender == null)
        return;


    if (anchorRender.getPos() == null)
        return;


    Vector3d vec =
        BlockHelper.vec3(anchorRender.getPos());


    if (NametagUtils.to2D(vec, 1.3)) {

        NametagUtils.begin(vec);

        TextRenderer.get()
            .begin(1,false,true);


        String text =
            anchorRender.getDamageTxt();


        TextRenderer.get()
            .render(
                text,
                0,
                0,
                new SettingColor(
                    255,
                    255,
                    255
                )
            );


        TextRenderer.get().end();

        NametagUtils.end();
    }
}




@EventHandler
private void onTick(TickEvent.Post event) {


    if (mc.player == null ||
        mc.world == null)
        return;



    if (anchorRender != null) {

        anchorRender.tick();

        if (anchorRender.shouldRemove())
            anchorRender = null;
    }


    if (breakRender != null) {

        breakRender.tick();

        if (breakRender.shouldRemove())
            breakRender = null;
    }



    if (MathUtil.msPassed(lastAction) < 40)
        return;




    PlayerEntity newTarget =
        TargetUtils.getPlayerTarget(
            range.get(),
            SortPriority.LowestDistance
        );



    if (newTarget != target) {

        placePos = null;
        breakPos = null;

        cachedSphere.clear();
        lastTargetPos = null;
    }


    target = newTarget;



    if (TargetUtils.isBadTarget(
        target,
        range.get()
    ))
        return;



    FindItemResult anchor =
        InvUtils.findInHotbar(
            Items.RESPAWN_ANCHOR
        );


    FindItemResult glow =
        InvUtils.findInHotbar(
            Items.GLOWSTONE
        );



    if (!anchor.found() ||
        !glow.found())
        return;




    if (placeTimer <= 0) {

        placePos =
            findPlace();


        placeAnchor(placePos);


        placeTimer =
            placeDelay.get();

    } else {

        placeTimer--;
    }




    if (breakTimer <= 0) {


        if (breakPos != null)
            breakAnchor(breakPos);

        else
            breakAnchor(findBreak());



        breakTimer =
            breakDelay.get();


    } else {

        breakTimer--;
    }
}
private boolean validPlacement(AnchorPlacement p) {

    return p != null &&
           p.getPos() != null;
}



private void placeAnchor(AnchorPlacement placement) {


    if (!validPlacement(placement))
        return;


    if (target == null)
        return;



    BlockPos pos =
        placement.getPos();



    // prevent ghost placements
    if (BlockHelper.getBlock(pos) != Blocks.AIR)
        return;



    FindItemResult anchor =
        InvUtils.findInHotbar(
            Items.RESPAWN_ANCHOR
        );


    if (!anchor.found())
        return;



    breakPos = placement;



    boolean swapBack = false;



    if (!Interactions.isHolding(
        Items.RESPAWN_ANCHOR
    )) {

        swapBack = true;

        Interactions.setSlot(
            anchor.slot(),
            false
        );
    }



    BlockHelper.place(
        pos,
        anchor,
        rotate.get(),
        packetPlace.get()
    );



    lastAction =
        MathUtil.now();



    if (swapBack)
        Interactions.swapBack();
}





private void breakAnchor(AnchorPlacement placement) {


    if (!validPlacement(placement))
        return;


    if (target == null)
        return;



    BlockPos pos =
        placement.getPos();



    BlockHitResult hit =
        placement.getHit();



    if (hit == null)
        return;




    if (BlockHelper.getBlock(pos)
        != Blocks.RESPAWN_ANCHOR) {


        if (antiDesync.get() &&
            BlockHelper.getBlock(pos)
            == Blocks.GLOWSTONE) {


            PacketManager.startPacketMine(
                pos,
                true,
                true
            );


            PacketManager.finishPacketMine(
                pos,
                true,
                true
            );
        }


        breakPos = null;

        return;
    }




    FindItemResult glow =
        InvUtils.findInHotbar(
            Items.GLOWSTONE
        );


    FindItemResult anchor =
        InvUtils.findInHotbar(
            Items.RESPAWN_ANCHOR
        );



    if (!glow.found() ||
        !anchor.found())
        return;




    if (breakRender != null)
        breakRender = null;



    breakRender =
        new Renderers.SimpleAnchorRender(
            pos,
            3,
            new SettingColor(
                255,
                0,
                170,
                35
            ),
            new SettingColor(
                255,
                0,
                170
            ),
            new SettingColor(
                255,
                255,
                255
            ),
            8,
            placement.targetDamage()
        );




    Hand glowHand =
        glow.isOffhand()
            ? Hand.OFF_HAND
            : Hand.MAIN_HAND;



    Hand anchorHand =
        anchor.isOffhand()
            ? Hand.OFF_HAND
            : Hand.MAIN_HAND;




    PacketManager.sendInteract(
        glowHand,
        glow,
        hit,
        rotate.get(),
        packetPlace.get()
    );



    PacketManager.sendInteract(
        anchorHand,
        anchor,
        hit,
        false,
        packetPlace.get()
    );




    if (antiDesync.get()) {

        PacketManager.startPacketMine(
            pos,
            true,
            true
        );


        PacketManager.finishPacketMine(
            pos,
            true,
            true
        );
    }



    Interactions.setSlot(
        anchor.slot(),
        false
    );



    lastAction =
        MathUtil.now();
}






private AnchorPlacement findBreak() {


    if (target == null)
        return null;



    for (BlockPos pos :
        getCachedSphere()) {


        if (BlockHelper.getBlock(pos)
            != Blocks.RESPAWN_ANCHOR)
            continue;



        AnchorPlacement p =
            new AnchorPlacement(pos);



        if (p.selfDamage()
            > maxSelf.get())
            continue;



        if (antiSuicide.get() &&
            PlayerUtils.getTotalHealth()
            - p.selfDamage()
            <= 0)
            continue;



        return p;
    }


    return null;
}
private AnchorPlacement findPlace() {


    if (target == null)
        return null;



    AnchorPlacement best =
        null;


    double bestDamage = 0;



    for (BlockPos pos :
        getCachedSphere()) {



        if (!canPlace(pos))
            continue;



        if (pos.equals(target.getBlockPos())
            || pos.equals(target.getBlockPos().up()))
            continue;



        AnchorPlacement p =
            new AnchorPlacement(pos);



        double self =
            p.selfDamage();


        double damage =
            p.targetDamage();



        if (antiSuicide.get()
            &&
            PlayerUtils.getTotalHealth()
            - self <= 0)
            continue;



        if (self > maxSelf.get())
            continue;



        if (damage < minDamage.get())
            continue;



        if (damage > bestDamage) {

            bestDamage = damage;

            best = p;


            if (fastCalc.get())
                break;
        }
    }



    return best;
}







private boolean canPlace(BlockPos pos) {


    if (!BlockHelper.canPlace(pos))
        return false;



    if (BlockHelper.distanceTo(pos)
        > placeRange.get())
        return false;



    if (MathUtil.intersects(pos))
        return false;



    return true;
}








private ArrayList<BlockPos> getCachedSphere() {


    if (target == null)
        return new ArrayList<>();



    BlockPos current =
        target.getBlockPos();



    if (lastTargetPos == null ||
        !lastTargetPos.equals(current)) {



        cachedSphere =
            getSphereArray(
                target,
                xRadius.get(),
                yRadius.get()
            );



        lastTargetPos =
            current;
    }



    return cachedSphere;
}







private ArrayList<BlockPos> getSphereArray(
    PlayerEntity player,
    int x,
    int y
) {


    ArrayList<BlockPos> list =
        new ArrayList<>();


    BlockPos center =
        player.getBlockPos();



    BlockPos.Mutable mutable =
        new BlockPos.Mutable();




    for (int xx=-x; xx<=x; xx++) {


        for (int yy=-y; yy<=y; yy++) {


            for (int zz=-x; zz<=x; zz++) {



                mutable.set(
                    center
                )
                .move(
                    xx,
                    yy,
                    zz
                );


                BlockPos pos =
                    mutable.toImmutable();



                double dist =
                    center.getSquaredDistance(pos);



                if (dist <= x*x)
                    list.add(pos);
            }
        }
    }



    list.sort(
        Comparator.comparingDouble(
            PlayerUtils::distanceTo
        )
    );


    return list;
}








private boolean raytrace(
    Entity entity,
    BlockPos pos
) {


    return BlockHelper.canSee(
        entity,
        pos
    );
}

}
