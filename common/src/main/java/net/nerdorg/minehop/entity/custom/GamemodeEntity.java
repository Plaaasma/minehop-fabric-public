package net.nerdorg.minehop.entity.custom;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.networking.PacketHandler;

public class GamemodeEntity extends Zone {
    public GamemodeEntity(EntityType<? extends Mob> entityType, Level world) {
        super(entityType, world);
    }

    public static AttributeSupplier.Builder createResetEntityAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1000000);
    }

    @Override
    public void tick() {
        Level world = this.level();
        if (world instanceof ServerLevel serverWorld) {
            DataManager.MapData pairedMap = DataManager.getMap(this.getPairedMap());
            if (pairedMap == null) {
                this.kill(serverWorld);
            }
        }
        super.tick();
    }
}
