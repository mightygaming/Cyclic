package com.lothrazar.cyclic.block.fanslab;

import com.lothrazar.cyclic.base.TileEntityBase;
import com.lothrazar.cyclic.net.PacketPlayerFalldamage;
import com.lothrazar.cyclic.registry.PacketRegistry;
import com.lothrazar.cyclic.registry.TileRegistry;
import com.lothrazar.cyclic.util.UtilShape;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

public class TileFanSlab extends TileEntityBase implements ITickableTileEntity {

  static enum Fields {
    REDSTONE, RANGE, SPEED;
  }

  public static final int MIN_RANGE = 1;
  public static final int MAX_RANGE = 64;
  public static final int MIN_SPEED = 1;
  public static final int MAX_SPEED = 20;
  private int range = 5;
  private int speed = 5;

  public TileFanSlab() {
    super(TileRegistry.FANSLAB.get());
  }

  @Override
  public void tick() {
    boolean powered = this.isPowered();
    boolean previous = this.getBlockState().get(BlockFanSlab.POWERED);
    if (previous != powered) {
      this.world.setBlockState(pos, this.getBlockState().with(BlockFanSlab.POWERED, powered));
    }
    if (powered) {
      this.pushEntities();
    }
  }

  public int getRange() {
    return this.range;
  }

  private float getSpeedCalc() {
    return (this.speed) / 39F;
  }

  private int getCurrentRange() {
    Direction facing = getCurrentFacing();
    BlockPos tester;
    for (int i = MIN_RANGE; i <= this.getRange(); i++) {
      //if we start at fan, we hit MYSELF (the fan)
      tester = this.getPos().offset(facing, i);
      if (canBlowThrough(tester) == false) {
        return i; //cant pass thru
      }
    }
    return getRange();
  }

  private boolean canBlowThrough(BlockPos tester) {
    return !world.getBlockState(tester).isSolid();
  }

  public List<BlockPos> getShape() {
    return UtilShape.line(getPos(), getCurrentFacing(), getCurrentRange());
  }

  @Override
  public Direction getCurrentFacing() {
    switch (this.getBlockState().get(BlockFanSlab.FACE)) {
      case CEILING:
        return Direction.DOWN;
      case FLOOR:
        return Direction.UP;
      default:
        return this.getBlockState().get(BlockFanSlab.HORIZONTAL_FACING);
    }
  }

  private int pushEntities() {
    List<BlockPos> shape = getShape();
    if (shape.size() == 0) {
      // sometimes is empty on changing dimension or tile load/unload
      return 0;
    }
    BlockPos start = shape.get(0);
    BlockPos end = shape.get(shape.size() - 1); //without this hotfix, fan works only on the flatedge of the band, not the 1x1 area
    switch (getCurrentFacing().getAxis()) {
      case X:
        end = end.add(0, 0, 1); //X means EASTorwest. adding +1z means GO 1 south
        end = end.add(0, 1, 0); //and of course go up one space. so we have a 3D range selected not a flat slice (ex: height 66 to 67)
      break;
      case Z:
        end = end.add(1, 0, 0);
        end = end.add(0, 1, 0); //and of course go up one space. so we have a 3D range selected not a flat slice (ex: height 66 to 67)
      break;
      case Y:
        start = start.add(1, 0, 0);
        end = end.add(0, 0, 1);
      break;
    }
    //ok now we have basically teh 3d box we wanted
    //problem: NORTH and WEST are skipping first blocks right at fan, but shouldnt.
    //EAST and SOUTH are skiping LAST blocks, but shouldnt
    //just fix it. root cause seems fine esp with UtilShape used
    Direction face = getCurrentFacing();
    switch (face) {
      case NORTH:
        start = start.south();
      break;
      case SOUTH:
        end = end.south();
      break;
      case EAST:
        end = end.east();
      break;
      case WEST:
        start = start.east();
      break;
      case DOWN:
      break;
      case UP:
      default:
      break;
    }
    AxisAlignedBB region = new AxisAlignedBB(start, end);
    List<Entity> entitiesFound = this.getWorld().getEntitiesWithinAABB(Entity.class, region);
    int moved = 0;
    boolean doPush = true; // TODO this toggle
    int direction = 1;
    float speed = this.getSpeedCalc();
    for (Entity entity : entitiesFound) {
      if (entity instanceof PlayerEntity && ((PlayerEntity) entity).isCrouching()) {
        continue; //sneak avoid feature
      }
      moved++;
      double newx = entity.getMotion().getX();
      double newy = entity.getMotion().getY();
      double newz = entity.getMotion().getZ();
      switch (face) {
        case NORTH:
          direction = !doPush ? 1 : -1;
          newz += direction * speed;
        break;
        case SOUTH:
          direction = doPush ? 1 : -1;
          newz += direction * speed;
        break;
        case EAST:
          direction = doPush ? 1 : -1;
          newx += direction * speed;
        break;
        case WEST:
          direction = !doPush ? 1 : -1;
          newx += direction * speed;
        break;
        case DOWN:
          direction = !doPush ? 1 : -1;
          newy += direction * speed;
        break;
        case UP:
          direction = doPush ? 1 : -1;
          newy += direction * speed;
        break;
      }
      entity.setMotion(newx, newy, newz);
      if (world.isRemote && entity.ticksExisted % PacketPlayerFalldamage.TICKS_FALLDIST_SYNC == 0
          && entity instanceof PlayerEntity) {
        PacketRegistry.INSTANCE.sendToServer(new PacketPlayerFalldamage());
      }
    }
    return moved;
  }

  @Override
  public void read(BlockState bs, CompoundNBT tag) {
    speed = tag.getInt("speed");
    range = tag.getInt("range");
    super.read(bs, tag);
  }

  @Override
  public CompoundNBT write(CompoundNBT tag) {
    tag.putInt("speed", speed);
    tag.putInt("range", range);
    return super.write(tag);
  }

  @Override
  public int getField(int f) {
    switch (Fields.values()[f]) {
      case RANGE:
        return range;
      case REDSTONE:
        return this.needsRedstone;
      case SPEED:
        return this.speed;
    }
    return 0;
  }

  @Override
  public void setField(int field, int value) {
    Fields f = Fields.values()[field];
    switch (f) {
      case RANGE:
        range = value;
        if (range < MIN_RANGE) {
          range = MIN_RANGE;
        }
        if (range > MAX_RANGE) {
          range = MAX_RANGE;
        }
      break;
      case REDSTONE:
        this.needsRedstone = value % 2;
      break;
      case SPEED:
        speed = value;
        if (speed < MIN_SPEED) {
          speed = MIN_SPEED;
        }
        if (speed > MAX_SPEED) {
          speed = MAX_SPEED;
        }
      break;
    }
  }
}
