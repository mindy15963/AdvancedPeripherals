package de.srendi.advancedperipherals.common.addons.appliedenergistics;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;

import dan200.computercraft.api.lua.ILuaCallback;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import de.srendi.advancedperipherals.common.util.ServerWorker;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class CraftJob implements ICraftingCallback, ILuaCallback {

    private final IComputerAccess computer;
    private final IGridNode node;
    private final IActionSource source;
    private final String item;
    private final Optional<Integer> count;
    private final World world;

    private MethodResult result;
    private LuaException exception;

    public CraftJob(World world, final IComputerAccess computer, IGridNode node, String item, final Optional<Integer> count, IActionSource source) {
        this.computer = computer;
        this.node = node;
        this.world = world;
        this.source = source;
        this.item = item;
        this.count = count;
    }

    public void startCrafting() {
        IGrid grid = node.getGrid();
        if (grid == null) {
            result = MethodResult.of(null, "grid is null");
            exception = new LuaException("grid is null");
            return;
        }

        final IStorageGrid storage = grid.getCache(IStorageGrid.class);
        final ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
        IMEMonitor<IAEItemStack> monitor = storage.getInventory(AppEngApi.getInstance().getApi().storage().getStorageChannel(IItemStorageChannel.class));
        ItemStack itemstack = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(item)));
        IAEItemStack aeItem = AppEngApi.getInstance().findAEStackFromItemStack(monitor, itemstack);
        if (aeItem == null) {
            result = MethodResult.of(null, item + " does not exists in the me system");
            exception = new LuaException(item + " is not craftable");
            return;
        }
        if (!aeItem.isCraftable()) {
            result = MethodResult.of(null, item + " is not craftable");
            exception = new LuaException(item + " is not craftable");
            return;
        }
        count.ifPresent(aeItem::setStackSize);

        crafting.beginCraftingJob(world, grid, this.source, aeItem, this);

    }

    @Override
    public void calculationComplete(ICraftingJob job) {
        ServerWorker.add(()->calcComplete(job));
    }

    private void calcComplete(ICraftingJob job) {
        if (job.isSimulation()) {
            result = MethodResult.of(false, "the me system has no ingredients for the crafting job");
            exception = new LuaException("the me system has no ingredients for the crafting job");
            return;
        }

        IGrid grid = node.getGrid();
        if (grid == null) {
            result = MethodResult.of(null, "not connected");
            exception = new LuaException("not connected");
            return;
        }
        final IStorageGrid storage = grid.getCache(IStorageGrid.class);
        IMEMonitor<IAEItemStack> monitor = storage.getInventory(AppEngApi.getInstance().getApi().storage().getStorageChannel(IItemStorageChannel.class));
        final ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
        final ICraftingLink link = crafting.submitJob(job, null, null, false, this.source);
        if (link == null) {
            result = MethodResult.of(false, "an unexpected error has occurred");
            exception = new LuaException("grid is null");
        } else {
            result = MethodResult.of(AppEngApi.getInstance().getObjectFromJob(job));
        }
    }

    @NotNull
    @Override
    public MethodResult resume(Object[] objects) {
        if (result != null) {
            return result;
        }
        if (exception != null) {
            return MethodResult.of(exception);
        }
        return null;
    }
}