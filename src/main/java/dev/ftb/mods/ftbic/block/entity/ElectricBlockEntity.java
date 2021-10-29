package dev.ftb.mods.ftbic.block.entity;

import dev.ftb.mods.ftbic.block.ElectricBlock;
import dev.ftb.mods.ftbic.block.ElectricBlockState;
import dev.ftb.mods.ftbic.recipe.RecipeCache;
import dev.ftb.mods.ftbic.util.PowerTier;
import dev.ftb.mods.ftbic.util.TieredEnergyStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TickableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.CapabilityEnergy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicLong;

public class ElectricBlockEntity extends BlockEntity implements TickableBlockEntity, TieredEnergyStorage {
	private static final AtomicLong ELECTRIC_NETWORK_CHANGES = new AtomicLong(0L);

	public static void electricNetworkUpdated(LevelAccessor level, BlockPos pos) {
		// TODO: Possibly implement some kind of localized network change counter. But for now, this works
		ELECTRIC_NETWORK_CHANGES.incrementAndGet();
	}

	public static long getCurrentElectricNetwork(LevelAccessor level, BlockPos pos) {
		return ELECTRIC_NETWORK_CHANGES.get();
	}

	private boolean changed = false;
	public int energy = 0;
	public int energyCapacity = 40000;
	public int energyAdded = 0;
	public PowerTier outputPowerTier = null;
	public PowerTier inputPowerTier = null;
	private LazyOptional<?> thisOptional = null;
	public ElectricBlockState changeState = null;

	public ElectricBlockEntity(BlockEntityType<?> type) {
		super(type);
	}

	public void writeData(CompoundTag tag) {
		tag.putInt("Energy", energy);
	}

	public void readData(CompoundTag tag) {
		energy = tag.getInt("Energy");
	}

	@Override
	public void load(BlockState state, CompoundTag tag) {
		super.load(state, tag);
		readData(tag);
	}

	@Override
	public CompoundTag save(CompoundTag tag) {
		super.save(tag);
		writeData(tag);
		return tag;
	}

	public LazyOptional<?> getThisOptional() {
		if (thisOptional == null) {
			thisOptional = LazyOptional.of(() -> this);
		}

		return thisOptional;
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();

		if (thisOptional != null) {
			thisOptional.invalidate();
			thisOptional = null;
		}
	}

	@NotNull
	@Override
	public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
		if (cap == CapabilityEnergy.ENERGY) {
			return getThisOptional().cast();
		}

		return super.getCapability(cap, side);
	}

	protected void handleEnergyInput() {
		if (level.isClientSide()) {
			return;
		}

		if (inputPowerTier != null && energyAdded > 0) {
			if (energyAdded > inputPowerTier.transferRate) {
				// TODO: Burn the machine if config is enabled
			}

			if (energy < energyCapacity) {
				energy += Math.min(energyAdded, energyCapacity - energy);

				if (energy == energyCapacity) {
					setChanged();
				}
			}

			energyAdded = 0;
		}
	}

	protected void handleChanges() {
		if (changeState != null) {
			level.setBlock(worldPosition, getBlockState().setValue(((ElectricBlock) getBlockState().getBlock()).electricBlockInstance.stateProperty, changeState), 3);
			changeState = null;
			setChanged();
		}

		if (changed) {
			changed = false;
			level.blockEntityChanged(worldPosition, this);
		}
	}

	@Override
	public void tick() {
		handleEnergyInput();
		handleChanges();
	}

	@Override
	public void setChanged() {
		changed = true;
	}

	@Override
	public int receiveEnergy(int maxReceive, boolean simulate) {
		if (!canReceive()) {
			return 0;
		}

		int energyReceived = Math.min(energyCapacity - energy, maxReceive);

		if (!simulate) {
			energyAdded += energyReceived;
		}

		return energyReceived;
	}

	@Override
	public int extractEnergy(int maxExtract, boolean simulate) {
		if (!canExtract()) {
			return 0;
		}

		int energyExtracted = Math.min(energy, maxExtract);

		if (!simulate) {
			energy -= energyExtracted;
			setChanged();
		}

		return energyExtracted;
	}

	@Override
	public int getEnergyStored() {
		return energy;
	}

	@Override
	public int getMaxEnergyStored() {
		return energyCapacity;
	}

	@Override
	public boolean canExtract() {
		return outputPowerTier != null;
	}

	@Override
	public boolean canReceive() {
		return inputPowerTier != null;
	}

	public InteractionResult rightClick(Player player, InteractionHand hand, BlockHitResult hit) {
		return InteractionResult.PASS;
	}

	@Override
	@Nullable
	public final PowerTier getInputPowerTier() {
		return inputPowerTier;
	}

	@Nullable
	@Override
	public PowerTier getOutputPowerTier() {
		return outputPowerTier;
	}

	@Nullable
	public RecipeCache getRecipeCache() {
		return level == null ? null : RecipeCache.get(level);
	}
}
