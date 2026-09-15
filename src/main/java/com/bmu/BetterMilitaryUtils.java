package com.bmu;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Explosion;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LevelAccessor;

@Mod(BetterMilitaryUtils.MODID)
public class BetterMilitaryUtils
{
    public static final String MODID = "bmu";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final Map<UUID, BlockPos> SELECTED_POST = new HashMap<>();

    // Кастомный ковер
    public static class CustomCarpetBlock extends Block {
        private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);

        public CustomCarpetBlock(Properties properties) {
            super(properties);
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
            return true;
        }
    }

    // Замаскированный ковер
    public static class DisguisedCarpetBlock extends CustomCarpetBlock {
        public DisguisedCarpetBlock(Properties properties) {
            super(properties);
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            return Shapes.empty();
        }
    }

    public static final RegistryObject<Block> DIRT_CARPET = BLOCKS.register("dirt_carpet",
            () -> new CustomCarpetBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DIRT)
                    .strength(0.1F)
                    .sound(SoundType.GRAVEL)
            ));

    // Камуфляжный блок
    public static class CamoNetBlock extends MultifaceBlock {
        private final MultifaceSpreader spreader;

        public CamoNetBlock(Properties properties) {
            super(properties);
            this.spreader = new MultifaceSpreader(
                    new MultifaceSpreader.DefaultSpreaderConfig(this));
        }

        @Override
        public MultifaceSpreader getSpreader() {
            return this.spreader;
        }
    }

    public enum FillType implements net.minecraft.util.StringRepresentable {
        EMPTY("empty"),
        GRAVEL("gravel"),
        SAND("sand"),
        DIRT("dirt"),
        CONCRETE("concrete");

        private final String name;

        FillType(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    // ===================== HESCO Бастион =====================
    public static class HescoBastionBlock extends Block {

        public static final EnumProperty<FillType> FILL =
                EnumProperty.create("fill", FillType.class);

        public HescoBastionBlock(Properties properties) {
            super(properties);
            this.registerDefaultState(this.stateDefinition.any().setValue(FILL, FillType.EMPTY));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FILL);
        }

        private static final VoxelShape HOLLOW_SHAPE = Shapes.join(
                Shapes.block(),
                Block.box(2.0D, 2.0D, 2.0D, 14.0D, 16.0D, 14.0D),
                BooleanOp.ONLY_FIRST
        );

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            if (state.getValue(FILL) == FillType.EMPTY) {
                return HOLLOW_SHAPE;
            }
            return Shapes.block();
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            if (state.getValue(FILL) == FillType.EMPTY) {
                return HOLLOW_SHAPE;
            }
            return Shapes.block();
        }

        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                     InteractionHand hand, BlockHitResult hit) {
            if (state.getValue(FILL) != FillType.EMPTY) {
                return InteractionResult.PASS;
            }

            ItemStack heldStack = player.getItemInHand(hand);
            FillType type = getFillType(heldStack.getItem());
            if (type == null) {
                return InteractionResult.PASS;
            }

            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }

            level.setBlock(pos, state.setValue(FILL, type), 3);

            if (!player.getAbilities().instabuild) {
                heldStack.shrink(1);
            }

            level.playSound(null, pos, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.CONSUME;
        }

        private static FillType getFillType(Item item) {
            if (item == Items.GRAVEL) return FillType.GRAVEL;
            if (item == Items.SAND) return FillType.SAND;
            if (item == Items.DIRT) return FillType.DIRT;
            return null;
        }

        @Override
        public ItemStack getCloneItemStack(BlockState state, net.minecraft.world.phys.HitResult target,
                                           BlockGetter level, BlockPos pos, Player player) {
            ItemStack stack = super.getCloneItemStack(state, target, level, pos, player);
            FillType fill = state.getValue(FILL);

            if (fill != FillType.EMPTY) {
                stack.getOrCreateTagElement("BlockStateTag").putString("fill", fill.getSerializedName());
            }

            return stack;
        }

        @Override
        public float getExplosionResistance(BlockState state, BlockGetter level,
                                            BlockPos pos, Explosion explosion) {
            return state.getValue(FILL) != FillType.EMPTY ? 60.0F : 3.0F;
        }
    }

    public static final RegistryObject<Block> HESCO_BASTION_TAN = BLOCKS.register("hesco_bastion_tan",
            () -> new HescoBastionBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
            ));
    public static final RegistryObject<Item> HESCO_BASTION_TAN_ITEM = ITEMS.register("hesco_bastion_tan",
            () -> new BlockItem(HESCO_BASTION_TAN.get(), new Item.Properties()));

    public static final RegistryObject<Block> HESCO_BASTION_GREEN = BLOCKS.register("hesco_bastion_green",
            () -> new HescoBastionBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT)
                    .strength(5.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
            ));
    public static final RegistryObject<Item> HESCO_BASTION_GREEN_ITEM = ITEMS.register("hesco_bastion_green",
            () -> new BlockItem(HESCO_BASTION_GREEN.get(), new Item.Properties()));

    // Куча мешков с песком (1-4 штуки, кладутся друг на друга как кирпичи).
    // Ориентация по X или Z — ложится поперёк направления взгляда игрока.
    public static class SandbagBlock extends Block {
        public static final IntegerProperty COUNT = IntegerProperty.create("count", 1, 4);
        public static final EnumProperty<Direction.Axis> AXIS =
                EnumProperty.create("axis", Direction.Axis.class, Direction.Axis.X, Direction.Axis.Z);

        private static final VoxelShape[] SHAPES_X = {
                Block.box(0, 0, 4, 16, 4, 12),
                Block.box(0, 0, 4, 16, 8, 12),
                Block.box(0, 0, 4, 16, 12, 12),
                Block.box(0, 0, 4, 16, 16, 12)
        };
        private static final VoxelShape[] SHAPES_Z = {
                Block.box(4, 0, 0, 12, 4, 16),
                Block.box(4, 0, 0, 12, 8, 16),
                Block.box(4, 0, 0, 12, 12, 16),
                Block.box(4, 0, 0, 12, 16, 16)
        };

        public SandbagBlock(Properties properties) {
            super(properties);
            this.registerDefaultState(this.stateDefinition.any()
                    .setValue(COUNT, 1)
                    .setValue(AXIS, Direction.Axis.X));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(COUNT, AXIS);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            Direction.Axis facingAxis = context.getHorizontalDirection().getAxis();
            Direction.Axis bagAxis = (facingAxis == Direction.Axis.X) ? Direction.Axis.Z : Direction.Axis.X;
            return this.defaultBlockState().setValue(AXIS, bagAxis);
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            int index = state.getValue(COUNT) - 1;
            return (state.getValue(AXIS) == Direction.Axis.Z) ? SHAPES_Z[index] : SHAPES_X[index];
        }

        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                     InteractionHand hand, BlockHitResult hit) {
            ItemStack held = player.getItemInHand(hand);
            if (held.is(SANDBAG_ITEM.get())) {
                int c = state.getValue(COUNT);
                if (c < 4) {
                    if (!level.isClientSide) {
                        level.setBlock(pos, state.setValue(COUNT, c + 1), 3);
                        if (!player.getAbilities().instabuild) {
                            held.shrink(1);
                        }
                        level.playSound(null, pos, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                    }
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        }

        @Override
        public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
            return 12.0F;
        }
    }

    public static final RegistryObject<Block> SANDBAG = BLOCKS.register("sandbag",
            () -> new SandbagBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SAND)
                    .strength(1.0F)
                    .sound(net.minecraft.world.level.block.SoundType.SAND)
                    .noOcclusion()
            ));
    public static final RegistryObject<Item> SANDBAG_ITEM = ITEMS.register("sandbag",
            () -> new BlockItem(SANDBAG.get(), new Item.Properties()));
    // ===================== Противодронная сетка =====================
    // Опора (тонкая стойка по центру с динамическими коннекторами)
    public static class WirePostBlock extends Block {
        private static final VoxelShape BASE = Block.box(6.0D, 0.0D, 6.0D, 10.0D, 16.0D, 10.0D);

        public WirePostBlock(Properties properties) {
            super(properties);
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return BASE;
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return BASE;
        }

        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                     InteractionHand hand, BlockHitResult hit) {
            if (level.isClientSide) return InteractionResult.SUCCESS;

            ItemStack held = player.getItemInHand(hand);
            boolean roofMode = held.getItem() == DRONE_WIRE_ROOF_ITEM.get().asItem();
            boolean wallMode = held.getItem() == DRONE_WIRE_ITEM.get().asItem();

            if (!roofMode && !wallMode) {
                return InteractionResult.PASS;
            }

            UUID id = player.getUUID();
            BlockPos first = SELECTED_POST.get(id);

            if (first != null && !level.getBlockState(first).is(DRONE_WIRE_POST.get())) {
                SELECTED_POST.remove(id);
                first = null;
            }

            if (first == null) {
                SELECTED_POST.put(id, pos.immutable());
                level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0F, 1.5F);
                return InteractionResult.CONSUME;
            }

            SELECTED_POST.remove(id);
            if (first.equals(pos)) return InteractionResult.CONSUME;

            if (roofMode) {
                if (first.getY() != pos.getY()) return InteractionResult.CONSUME;

                int minX = Math.min(first.getX(), pos.getX());
                int maxX = Math.max(first.getX(), pos.getX());
                int minZ = Math.min(first.getZ(), pos.getZ());
                int maxZ = Math.max(first.getZ(), pos.getZ());

                int width = maxX - minX + 1;
                int depth = maxZ - minZ + 1;
                if (width > 8 || depth > 8) return InteractionResult.CONSUME;

                int area = width * depth - 2;
                if (area <= 0) return InteractionResult.CONSUME;

                if (!player.getAbilities().instabuild && countWire(player, DRONE_WIRE_ROOF_ITEM.get().asItem()) < area) {
                    level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.0F, 0.5F);
                    return InteractionResult.CONSUME;
                }

                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos p = new BlockPos(x, pos.getY(), z);
                        if (level.getBlockState(p).isAir()) {
                            level.setBlock(p, DRONE_WIRE_ROOF.get().defaultBlockState(), 3);
                        }
                    }
                }

                if (!player.getAbilities().instabuild) {
                    consume(player, held, DRONE_WIRE_ROOF_ITEM.get().asItem(), area);
                }

                level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                return InteractionResult.CONSUME;
            }

            // ===== СТЕНА (одна линия на высоте клика) =====
            if (first.getY() != pos.getY()) return InteractionResult.CONSUME;

            boolean axisX = first.getZ() == pos.getZ() && first.getX() != pos.getX();
            boolean axisZ = first.getX() == pos.getX() && first.getZ() != pos.getZ();
            if (!axisX && !axisZ) return InteractionResult.CONSUME;

            Direction dir = axisX
                    ? (first.getX() < pos.getX() ? Direction.EAST : Direction.WEST)
                    : (first.getZ() < pos.getZ() ? Direction.SOUTH : Direction.NORTH);

            int dist = Math.abs(first.distManhattan(pos));
            if (dist > 16) return InteractionResult.CONSUME;
            int fillCount = dist - 1;
            if (fillCount <= 0) return InteractionResult.CONSUME;

            if (!player.getAbilities().instabuild && countWire(player, DRONE_WIRE_ITEM.get().asItem()) < fillCount) {
                level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.0F, 0.5F);
                return InteractionResult.CONSUME;
            }

            Direction.Axis axis = axisX ? Direction.Axis.X : Direction.Axis.Z;

            BlockPos cursor = first.relative(dir);
            while (!cursor.equals(pos)) {
                if (!level.getBlockState(cursor).is(DRONE_WIRE_POST.get()) && level.getBlockState(cursor).isAir()) {
                    BlockState ws = DRONE_WIRE.get().defaultBlockState()
                            .setValue(WireNetBlock.AXIS, axis);
                    ws = WireNetBlock.applyConnections(ws, level, cursor);
                    level.setBlock(cursor, ws, 3);
                }
                cursor = cursor.relative(dir);
            }

            if (!player.getAbilities().instabuild) {
                consume(player, held, DRONE_WIRE_ITEM.get().asItem(), fillCount);
            }

            level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.CONSUME;
        }

        private static int countWire(Player player, Item wireItem) {
            int total = 0;
            for (ItemStack stack : player.getInventory().items) {
                if (stack.getItem() == wireItem) total += stack.getCount();
            }
            return total;
        }

        private static void consume(Player player, ItemStack held, Item wireItem, int amount) {
            int remaining = amount;
            if (held.getItem() == wireItem) {
                int take = Math.min(held.getCount(), remaining);
                held.shrink(take);
                remaining -= take;
            }
            for (ItemStack stack : player.getInventory().items) {
                if (remaining <= 0) break;
                if (stack.getItem() == wireItem) {
                    int take = Math.min(stack.getCount(), remaining);
                    stack.shrink(take);
                    remaining -= take;
                }
            }
        }

        @Override
        public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
            if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
                removeAdjacentWires(level, pos);
            }
            super.onRemove(state, level, pos, newState, moving);
        }

        private void removeAdjacentWires(Level level, BlockPos pos) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                removeLine(level, pos, d, 16);
            }
            removeLine(level, pos, Direction.UP, 16);
            removeLine(level, pos, Direction.DOWN, 16);
        }

        private void removeLine(Level level, BlockPos pos, Direction d, int max) {
            BlockPos cursor = pos.relative(d);
            int i = 0;
            while (i < max) {
                BlockState st = level.getBlockState(cursor);
                if (st.is(DRONE_WIRE.get())) {
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 3);
                } else if (st.is(DRONE_WIRE_POST.get())) {
                    break;
                } else {
                    break;
                }
                cursor = cursor.relative(d);
                i++;
            }
        }
    }
    // Проволока (сетка из нитей, тонкий блок)
// ===================== Проволока с динамическими коннекторами =====================
    public static class WireNetBlock extends Block {
        public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
        public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
        public static final BooleanProperty EAST  = BlockStateProperties.EAST;
        public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
        public static final BooleanProperty WEST  = BlockStateProperties.WEST;

        // Физика проволоки по центру
        protected static final VoxelShape SHAPE_X = Block.box(0.0D, 7.0D, 7.0D, 16.0D, 9.0D, 9.0D);
        protected static final VoxelShape SHAPE_Z = Block.box(7.0D, 7.0D, 0.0D, 9.0D, 9.0D, 16.0D);

        public WireNetBlock(Properties properties) {
            super(properties);
            this.registerDefaultState(this.stateDefinition.any()
                    .setValue(AXIS, Direction.Axis.X)
                    .setValue(NORTH, false)
                    .setValue(EAST, false)
                    .setValue(SOUTH, false)
                    .setValue(WEST, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(AXIS, NORTH, EAST, SOUTH, WEST);
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return state.getValue(AXIS) == Direction.Axis.X ? SHAPE_X : SHAPE_Z;
        }

        // Метод проверки соседей для подключения коннекторов
        public static BlockState applyConnections(BlockState state, LevelAccessor level, BlockPos pos) {
            Direction.Axis axis = state.getValue(AXIS);

            boolean east  = axis == Direction.Axis.X && level.getBlockState(pos.east()).getBlock() instanceof WirePostBlock;
            boolean west  = axis == Direction.Axis.X && level.getBlockState(pos.west()).getBlock() instanceof WirePostBlock;
            boolean north = axis == Direction.Axis.Z && level.getBlockState(pos.north()).getBlock() instanceof WirePostBlock;
            boolean south = axis == Direction.Axis.Z && level.getBlockState(pos.south()).getBlock() instanceof WirePostBlock;

            return state.setValue(NORTH, north).setValue(EAST, east)
                    .setValue(SOUTH, south).setValue(WEST, west);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            Direction.Axis axis = context.getHorizontalDirection().getAxis();
            BlockState state = this.defaultBlockState().setValue(AXIS, axis);
            return applyConnections(state, context.getLevel(), context.getClickedPos());
        }

        @Override
        public BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos pos, BlockPos facingPos) {
            return applyConnections(state, level, pos);
        }
    }
    // Горизонтальная сетка-крыша
    public static class RoofWireBlock extends Block {
        private static final VoxelShape SHAPE = Block.box(0.0D, 7.0D, 0.0D, 16.0D, 9.0D, 16.0D);

        public RoofWireBlock(Properties properties) {
            super(properties);
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }

        @Override
        public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            return true;
        }
    }

    public static final RegistryObject<Block> DRONE_WIRE_POST = BLOCKS.register("drone_wire_post",
            () -> new WirePostBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
            ));

    public static final RegistryObject<Block> DRONE_WIRE = BLOCKS.register("drone_wire",
            () -> new WireNetBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
            ));

    public static final RegistryObject<Block> DRONE_WIRE_ROOF = BLOCKS.register("drone_wire_roof",
            () -> new RoofWireBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
            ));

    public static final RegistryObject<Item> DRONE_WIRE_ROOF_ITEM = ITEMS.register("drone_wire_roof",
            () -> new BlockItem(DRONE_WIRE_ROOF.get(), new Item.Properties()));

    public static final RegistryObject<Item> DRONE_WIRE_POST_ITEM = ITEMS.register("drone_wire_post",
            () -> new BlockItem(DRONE_WIRE_POST.get(), new Item.Properties()));

    public static final RegistryObject<Item> DRONE_WIRE_ITEM = ITEMS.register("drone_wire",
            () -> new BlockItem(DRONE_WIRE.get(), new Item.Properties()));
    public static final RegistryObject<Item> DIRT_CARPET_ITEM = ITEMS.register("dirt_carpet",
            () -> new BlockItem(DIRT_CARPET.get(), new Item.Properties()));

    public static final RegistryObject<Block> GRASS_CARPET = BLOCKS.register("grass_carpet",
            () -> new CustomCarpetBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GRASS)
                    .strength(0.1F)
                    .sound(SoundType.GRASS)
            ));
    public static final RegistryObject<Item> GRASS_CARPET_ITEM = ITEMS.register("grass_carpet",
            () -> new BlockItem(GRASS_CARPET.get(), new Item.Properties()));

    public static final RegistryObject<Block> DISGUISED_DIRT_CARPET = BLOCKS.register("disguised_dirt_carpet",
            () -> new DisguisedCarpetBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DIRT)
                    .strength(0.1F)
                    .sound(SoundType.GRAVEL)
                    .noCollission()
            ));
    public static final RegistryObject<Item> DISGUISED_DIRT_CARPET_ITEM = ITEMS.register("disguised_dirt_carpet",
            () -> new BlockItem(DISGUISED_DIRT_CARPET.get(), new Item.Properties()));

    public static final RegistryObject<Block> DISGUISED_GRASS_CARPET = BLOCKS.register("disguised_grass_carpet",
            () -> new DisguisedCarpetBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GRASS)
                    .strength(0.1F)
                    .sound(SoundType.GRASS)
                    .noCollission()
            ));
    public static final RegistryObject<Item> DISGUISED_GRASS_CARPET_ITEM = ITEMS.register("disguised_grass_carpet",
            () -> new BlockItem(DISGUISED_GRASS_CARPET.get(), new Item.Properties()));

    public static final RegistryObject<Block> CAMO_NET = BLOCKS.register("camo_net",
            () -> new CamoNetBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT)
                    .strength(0.3F)
                    .sound(SoundType.WOOL)
                    .noOcclusion()
            ));
    public static final RegistryObject<Item> CAMO_NET_ITEM = ITEMS.register("camo_net",
            () -> new BlockItem(CAMO_NET.get(), new Item.Properties()));

    public BetterMilitaryUtils(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();

        modEventBus.addListener(this::commonSetup);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);
        modEventBus.addListener(this::addCreative);

        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private static int getCamoColorForBiome(net.minecraft.resources.ResourceLocation biomeId) {
        if (biomeId == null) return 0x4A6B3A;

        String path = biomeId.getPath();

        if (path.contains("desert") || path.contains("badlands") || path.contains("savanna")) {
            return 0xC2A464;
        }
        if (path.contains("snowy") || path.contains("ice") || path.contains("frozen") || path.contains("grove")) {
            return 0xD8E4E8;
        }
        if (path.contains("swamp") || path.contains("mangrove") || path.contains("jungle")) {
            return 0x3B5C3A;
        }
        if (path.contains("mountain") || path.contains("hills") || path.contains("peaks") || path.contains("stony")) {
            return 0x8A8A82;
        }

        return 0x4A6B3A;
    }

    public static final RegistryObject<CreativeModeTab> BMU_TAB = CREATIVE_MODE_TABS.register("bmu_tab",
            () -> CreativeModeTab.builder()
                    .title(net.minecraft.network.chat.Component.translatable("itemGroup.bmu"))
                    .icon(() -> new ItemStack(HESCO_BASTION_TAN_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(DIRT_CARPET_ITEM.get());
                        output.accept(GRASS_CARPET_ITEM.get());
                        output.accept(DISGUISED_DIRT_CARPET_ITEM.get());
                        output.accept(DISGUISED_GRASS_CARPET_ITEM.get());
                        output.accept(CAMO_NET_ITEM.get());
                        output.accept(HESCO_BASTION_TAN_ITEM.get());
                        output.accept(HESCO_BASTION_GREEN_ITEM.get());
                        output.accept(DRONE_WIRE_ITEM.get());
                        output.accept(DRONE_WIRE_ROOF_ITEM.get());
                        output.accept(DRONE_WIRE_POST_ITEM.get());
                        output.accept(SANDBAG_ITEM.get());

                    })
                    .build()
    );

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        LOGGER.info("HELLO FROM COMMON SETUP");
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        LOGGER.info("HELLO from server starting");
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            LOGGER.info("HELLO FROM CLIENT SETUP");
        }

        @SubscribeEvent
        public static void registerBlockColors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Block event) {
            event.getBlockColors().register((state, world, pos, tintIndex) -> {
                if (tintIndex == 1) {
                    return net.minecraft.client.renderer.BiomeColors.getAverageFoliageColor(world, pos);
                }
                if (world instanceof LevelReader levelReader && pos != null) {
                    net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biomeHolder = levelReader.getBiome(pos);
                    net.minecraft.resources.ResourceLocation biomeId = biomeHolder.unwrapKey()
                            .map(key -> key.location())
                            .orElse(null);
                    if (state.is(CAMO_NET.get())) {
                        return getCamoColorForBiome(biomeId);
                    }
                }
                return net.minecraft.client.renderer.BiomeColors.getAverageGrassColor(world, pos);
            }, GRASS_CARPET.get(), DISGUISED_GRASS_CARPET.get(), DISGUISED_DIRT_CARPET.get(), CAMO_NET.get());
        }

        @SubscribeEvent
        public static void registerItemColors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Item event) {
            event.getItemColors().register((stack, tintIndex) -> {
                if (tintIndex == 1) {
                    return net.minecraft.world.level.FoliageColor.getDefaultColor();
                }
                return net.minecraft.world.level.GrassColor.get(0.5D, 1.0D);
            }, GRASS_CARPET_ITEM.get(), DISGUISED_GRASS_CARPET_ITEM.get(), DISGUISED_DIRT_CARPET_ITEM.get());

            event.getItemColors().register((stack, tintIndex) -> 0x4A6B3A, CAMO_NET_ITEM.get());
        }
    }
}
