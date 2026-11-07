package dev.marigold.handinhand;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

@EventBusSubscriber(modid=HandInHand.ID)
public final class HandServer {
    private static final Map<MinecraftServer,ConsentLedger> STATES=new WeakHashMap<>();
    private static final Map<MinecraftServer,Map<UUID,LinkStability>> STABILITY=new WeakHashMap<>();
    private static final Map<MinecraftServer,Map<UUID,FlightRates>> FLIGHT=new WeakHashMap<>();
    private static final Map<MinecraftServer,Map<UUID,HandTether>> TETHERS=new WeakHashMap<>();
    private static final Map<MinecraftServer,Map<UUID,Boolean>> VERTICAL=new WeakHashMap<>();
    public static final ResourceLocation SPEED=ResourceLocation.fromNamespaceAndPath(HandInHand.ID,"shared_walk");
    private static final String OPT_OUT="handinhand_requests_off";
    public static ConsentLedger state(MinecraftServer s) {return STATES.computeIfAbsent(s,k->new ConsentLedger());}
    public static Boolean heldSide(ServerPlayer p) {
        var pair=state(p.server).pair(p.getUUID());if(pair==null)return null;
        var tether=TETHERS.computeIfAbsent(p.server,k->new HashMap<>()).get(pair.first());
        return tether==null?null:(p.getUUID().equals(pair.first())?tether.firstRight():tether.secondRight());
    }
    public static void intent(ServerPlayer p,TetherIntent message){
        if(!Float.isFinite(message.bodyYaw()))return;
        var pair=state(p.server).pair(p.getUUID());
        if(pair==null||pair.started()!=message.started()||!pair.other(p.getUUID()).equals(message.partner()))return;
        var session=TETHERS.computeIfAbsent(p.server,k->new HashMap<>()).get(pair.first());
        if(session!=null)session.intent(p.getUUID(),now(p.server),message.moving(),message.bodyYaw());
    }
    public static HandTether controller(ServerPlayer p){
        var pair=state(p.server).pair(p.getUUID());return pair==null?null:TETHERS.computeIfAbsent(p.server,k->new HashMap<>()).get(pair.first());
    }
    public static boolean isControlled(ServerPlayer p){
        if(!HandInHand.ENABLED.get()||!HandInHand.MOVEMENT_TETHER.get()||!compatible(p))return false;
        var t=controller(p);return t!=null&&t.controlling(p,now(p.server));
    }
    public static boolean isLifted(ServerPlayer p){var t=controller(p);return isControlled(p)&&t.lifting(p,now(p.server));}
    public static net.minecraft.world.phys.Vec3 constrain(ServerPlayer p,net.minecraft.world.phys.Vec3 proposed){
        var pair=state(p.server).pair(p.getUUID());if(pair==null||!HandInHand.MOVEMENT_TETHER.get()||!compatible(p))return proposed;
        var other=p.server.getPlayerList().getPlayer(pair.other(p.getUUID()));if(other==null||p.level()!=other.level())return proposed;
        double size=HandTether.scale(p,other);if(!Double.isFinite(size)||size<=0)return proposed;
        var move=PairReach.move(HandTether.vector(p.position()),HandTether.vector(other.position()),HandTether.vector(proposed.subtract(p.position())),1.3*size,2*size,false);
        return p.position().add(HandTether.vector(move));
    }
    private static long now(MinecraftServer s) {return s.overworld().getGameTime();}
    public static void tell(ServerPlayer p,String text) {if(p!=null) p.sendSystemMessage(Component.literal("[Hand in Hand] ").withStyle(ChatFormatting.GOLD).append(Component.literal(text).withStyle(ChatFormatting.GRAY)));}
    private static void notify(MinecraftServer s,ConsentLedger.Request r,String message) {
        tell(s.getPlayerList().getPlayer(r.sender()),message); tell(s.getPlayerList().getPlayer(r.recipient()),message);
    }
    public static boolean compatible(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && !p.isPassenger() && !p.isSleeping() && !p.isFallFlying()
            && (!p.getAbilities().flying||HandInHand.ALLOW_FLIGHT.get()) && (p.getPose()==Pose.STANDING||p.getPose()==Pose.CROUCHING) && !p.isInWaterOrBubble() && !p.isInLava();
    }
    private static void validate(ServerPlayer a,ServerPlayer b) { validate(a,b,false); }
    private static void validate(ServerPlayer a,ServerPlayer b,boolean grab) {
        if(!HandInHand.ENABLED.get()) throw new IllegalStateException("Hand holding is disabled on this server.");
        if(a==null||b==null) throw new IllegalStateException("That player is no longer online.");
        if(!grab&&b.getTags().contains(OPT_OUT)) throw new IllegalStateException("That player is not accepting requests.");
        if(!compatible(a)||!compatible(b)) throw new IllegalStateException("Both players must be upright or sneaking, outside water, and out of vehicles.");
        if(a.level()!=b.level()||a.distanceTo(b)>HandInHand.REQUEST_DISTANCE.get()||!a.hasLineOfSight(b))
            throw new IllegalStateException("Stand close together with a clear line of sight first.");
        if(!ScaleSupport.reachable(a,b)) throw new IllegalStateException("Your hands cannot reach from here. Move closer; very different sizes or non-uniform model scales may be unsupported.");
    }
    private static MutableComponent button(String label,String command,ChatFormatting color) {
        return Component.literal(label).withStyle(s->s.withColor(color).withBold(true)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,command))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Component.literal("Click to "+label.replace("[","").replace("]","").toLowerCase(Locale.ROOT)))));
    }
    public static ConsentLedger.Request ask(ServerPlayer a,ServerPlayer b) {
        validate(a,b);
        var r=state(a.server).request(a.getUUID(),b.getUUID(),now(a.server),HandInHand.EXPIRY.get()*20L,HandInHand.COOLDOWN.get()*20L);
        tell(a,"Request sent to "+b.getGameProfile().getName()+".");
        b.sendSystemMessage(Component.literal("[Hand in Hand] ").withStyle(ChatFormatting.GOLD).append(a.getDisplayName())
            .append(Component.literal(" would like to hold your hand.").withStyle(ChatFormatting.GRAY)));
        if(HandInHand.MOVEMENT_TETHER.get())tell(b,"While linked, either of you can lead a passive partner alongside you, including into the air while flying. You can release at any time with your hand-holding key or /handholding release.");
        b.sendSystemMessage(button("[Accept]","/handholding accept "+r.token(),ChatFormatting.GREEN).append("  ")
            .append(button("[Decline]","/handholding decline "+r.token(),ChatFormatting.RED)));
        a.sendSystemMessage(button("[Cancel]","/handholding cancel",ChatFormatting.YELLOW));
        return r;
    }
    public static ConsentLedger.Pair accept(ServerPlayer recipient,UUID token) {
        var ledger=state(recipient.server);
        var request=ledger.validate(recipient.getUUID(),token,now(recipient.server));
        ServerPlayer sender=recipient.server.getPlayerList().getPlayer(request.sender());
        validate(sender,recipient);
        var pair=ledger.accept(recipient.getUUID(),token,now(recipient.server));
        tell(sender,"You are holding hands with "+recipient.getGameProfile().getName()+". Press your release key or use /handholding release.");
        tell(recipient,"You are holding hands with "+sender.getGameProfile().getName()+". Press your release key or use /handholding release.");
        updateSpeed(sender,recipient);
        TETHERS.computeIfAbsent(recipient.server,k->new HashMap<>()).put(pair.first(),new HandTether(sender,recipient));
        syncFlight(pair,sender,recipient,true);
        return pair;
    }
    public static boolean mayGrab(ServerPlayer p){
        return grabBlock(p)==GrabPolicy.Block.NONE;
    }
    public static GrabPolicy.Block grabBlock(ServerPlayer p){return GrabPolicy.check(HandInHand.ENABLED.get(),HandInHand.ROLEPLAY_GRAB.get(),HandInHand.MOVEMENT_TETHER.get(),HandInHand.GRAB_REQUIRES_OP.get(),p.createCommandSourceStack().hasPermission(2)||p.server.getPlayerList().isOp(p.getGameProfile()));}
    private static String grabConfigStatus(){return "Roleplay grab: "+HandInHand.ROLEPLAY_GRAB.get()+"; OP only: "+HandInHand.GRAB_REQUIRES_OP.get()+"; movement following: "+HandInHand.MOVEMENT_TETHER.get()+"; hand holding: "+HandInHand.ENABLED.get()+". Enabling grab keeps OP-only policy; use /handholding config grabOpOnly false to allow non-OP players.";}
    private static boolean configureGrab(boolean value){
        boolean followingEnabled=value&&!HandInHand.MOVEMENT_TETHER.get();
        if(value)HandInHand.MOVEMENT_TETHER.set(true);
        HandInHand.ROLEPLAY_GRAB.set(value);HandInHand.CONFIG.save();
        HandInHand.ROLEPLAY_GRAB.clearCache();HandInHand.MOVEMENT_TETHER.clearCache();
        if(HandInHand.ROLEPLAY_GRAB.get()!=value||value&&!HandInHand.MOVEMENT_TETHER.get())throw new IllegalStateException("The saved roleplay-grab settings did not become active.");
        return followingEnabled;
    }
    public static ConsentLedger.Pair grab(ServerPlayer actor,ServerPlayer target){
        var blocked=grabBlock(actor);if(blocked!=GrabPolicy.Block.NONE)throw new IllegalStateException(GrabPolicy.message(blocked));
        validate(actor,target,true);
        var pair=state(actor.server).grab(actor.getUUID(),target.getUUID(),now(actor.server),HandInHand.COOLDOWN.get()*20L,true);
        TETHERS.computeIfAbsent(actor.server,k->new HashMap<>()).put(pair.first(),new HandTether(actor,target));
        syncFlight(pair,actor,target,true);
        tell(actor,"You grabbed "+target.getGameProfile().getName()+"'s hand. H releases.");
        tell(target,actor.getGameProfile().getName()+" grabbed your hand. Press H or use /handholding release to let go.");
        return pair;
    }
    public static void release(ServerPlayer actor,String reason) {
        var p=state(actor.server).release(actor.getUUID(),now(actor.server),HandInHand.COOLDOWN.get()*20L);
        if(p==null) return;
        STABILITY.computeIfAbsent(actor.server,k->new HashMap<>()).remove(p.first());
        FLIGHT.computeIfAbsent(actor.server,k->new HashMap<>()).remove(p.first());
        TETHERS.computeIfAbsent(actor.server,k->new HashMap<>()).remove(p.first());
        VERTICAL.computeIfAbsent(actor.server,k->new HashMap<>()).remove(p.first());
        for(UUID id:List.of(p.first(),p.second())) {
            ServerPlayer player=actor.server.getPlayerList().getPlayer(id);
            if(player!=null) { clearSpeed(player); tell(player,"Hands released"+(reason.isEmpty()?".":": "+reason+".")); }
        }
        clearSpeed(actor);
        PacketDistributor.sendToAllPlayers(new LinkPayload(p.first(),LinkPayload.NONE,0));
        PacketDistributor.sendToAllPlayers(new LinkPayload(p.second(),LinkPayload.NONE,0));
    }
    public static void clearSpeed(ServerPlayer p) {var a=p.getAttribute(Attributes.MOVEMENT_SPEED);if(a!=null)a.removeModifier(SPEED);}
    public static double naturalSpeed(AttributeInstance a) {
        double base=a.getBaseValue();
        for(var m:a.getModifiers()) if(!m.id().equals(SPEED)&&m.operation()==AttributeModifier.Operation.ADD_VALUE) base+=m.amount();
        double result=base;
        for(var m:a.getModifiers()) if(!m.id().equals(SPEED)&&m.operation()==AttributeModifier.Operation.ADD_MULTIPLIED_BASE) result+=base*m.amount();
        for(var m:a.getModifiers()) if(!m.id().equals(SPEED)&&m.operation()==AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) result*=1+m.amount();
        return a.getAttribute().value().sanitizeValue(result);
    }
    public static void updateSpeed(ServerPlayer a,ServerPlayer b) {
        if(!HandInHand.ALLOW_SPRINTING.get()){a.setSprinting(false);b.setSprinting(false);}
        if(HandInHand.MOVEMENT_TETHER.get()){clearSpeed(a);clearSpeed(b);return;}
        var aa=a.getAttribute(Attributes.MOVEMENT_SPEED);var bb=b.getAttribute(Attributes.MOVEMENT_SPEED);
        if(aa==null||bb==null)return;
        double av=naturalSpeed(aa),bv=naturalSpeed(bb),am=ScaleSupport.motionScale(a),bm=ScaleSupport.motionScale(b);
        double paceA=av,paceB=bv;
        if(HandInHand.MOVEMENT_TETHER.get()) {
            if(a.isSprinting()&&!b.isSprinting())paceB*=sprintFactor(aa);
            if(b.isSprinting()&&!a.isSprinting())paceA*=sprintFactor(bb);
        }
        double target=Math.min(paceA*am,paceB*bm);
        match(aa,av,am>1e-9?target/am:0);match(bb,bv,bm>1e-9?target/bm:0);
    }
    private static double sprintFactor(AttributeInstance attribute) {
        var sprint=attribute.getModifier(ResourceLocation.withDefaultNamespace("sprinting"));
        return sprint!=null&&sprint.operation()==AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL?Math.max(1,1+sprint.amount()):1;
    }
    private static LinkPayload payload(MinecraftServer server,ConsentLedger.Pair pair) {
        var f=FLIGHT.computeIfAbsent(server,k->new HashMap<>()).getOrDefault(pair.first(),FlightRates.NORMAL);
        var tether=TETHERS.computeIfAbsent(server,k->new HashMap<>()).get(pair.first());
        return new LinkPayload(pair.first(),pair.second(),pair.started(),f.horizontalA(),f.horizontalB(),f.verticalA(),f.verticalB(),VERTICAL.computeIfAbsent(server,k->new HashMap<>()).getOrDefault(pair.first(),false),tether==null||tether.firstRight(),tether!=null&&tether.secondRight());
    }
    private static void syncFlight(ConsentLedger.Pair pair,ServerPlayer a,ServerPlayer b,boolean force) {
        boolean vertical=a.getAbilities().flying&&b.getAbilities().flying;
        FlightRates rates=vertical&&!HandInHand.MOVEMENT_TETHER.get()?
            FlightRates.match(a.getAbilities().getFlyingSpeed(),b.getAbilities().getFlyingSpeed(),ScaleSupport.motionScale(a),ScaleSupport.motionScale(b),a.isSprinting(),b.isSprinting()):FlightRates.NORMAL;
        var cache=FLIGHT.computeIfAbsent(a.server,k->new HashMap<>());
        var old=cache.put(pair.first(),rates);
        var oldVertical=VERTICAL.computeIfAbsent(a.server,k->new HashMap<>()).put(pair.first(),vertical);
        if(force||!rates.equals(old)||!Objects.equals(oldVertical,vertical))PacketDistributor.sendToAllPlayers(payload(a.server,pair));
    }
    private static void match(AttributeInstance a,double original,double target) {
        double factor=original<1e-9?0:target/original-1;
        var existing=a.getModifier(SPEED);
        if(Math.abs(factor)<1e-7){if(existing!=null)a.removeModifier(SPEED);return;}
        if(existing==null||Math.abs(existing.amount()-factor)>1e-7){
            a.removeModifier(SPEED);a.addTransientModifier(new AttributeModifier(SPEED,factor,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post e) {
        var server=e.getServer();var ledger=state(server);
        for(var r:ledger.expire(now(server))) notify(server,r,"The hand-holding request expired.");
        for(var pair:ledger.pairs()) {
            ServerPlayer a=server.getPlayerList().getPlayer(pair.first()),b=server.getPlayerList().getPlayer(pair.second());
            if(a==null||b==null){ if(a!=null) release(a,"your partner left");else if(b!=null)release(b,"your partner left");else ledger.release(pair.first());continue;}
            if(!HandInHand.ENABLED.get()||!compatible(a)||!compatible(b)){release(a,"a player changed pose or movement mode");continue;}
            if(a.level()!=b.level()){release(a,"a player changed dimensions");continue;}
            if(pair.grab()&&!mayGrab(a)){release(a,"roleplay grabbing permission changed");continue;}
            boolean tether=HandInHand.MOVEMENT_TETHER.get();
            boolean hard=a.distanceTo(b)>HandInHand.RELEASE_DISTANCE.get();
            var stability=STABILITY.computeIfAbsent(server,k->new HashMap<>()).computeIfAbsent(pair.first(),k->new LinkStability());
            if(!tether&&stability.shouldRelease(!hard&&a.hasLineOfSight(b)&&ScaleSupport.reachable(a,b),hard,HandInHand.REACH_GRACE.get())){release(a,"you moved apart");continue;}
            if(a.isUsingItem()||b.isUsingItem()){release(a,"a player used an item");continue;}
            // Large teleports release even if the partner teleports to the same destination.
            if(!tether&&(a.position().distanceToSqr(new net.minecraft.world.phys.Vec3(a.xo,a.yo,a.zo))>4 || b.position().distanceToSqr(new net.minecraft.world.phys.Vec3(b.xo,b.yo,b.zo))>4)){release(a,"a player teleported");continue;}
            if(tether&&!TETHERS.computeIfAbsent(server,k->new HashMap<>()).computeIfAbsent(pair.first(),k->new HandTether(a,b)).tick(pair,a,b)){
                release(a,"a player teleported or changed scale incompatibly");continue;
            }
            updateSpeed(a,b);
            syncFlight(pair,a,b,TETHERS.get(server).get(pair.first()).consumeGripChange());
        }
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if(e.getEntity() instanceof ServerPlayer p){clearSpeed(p);for(var pair:state(p.server).pairs())PacketDistributor.sendToPlayer(p,payload(p.server,pair));}
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking e) {
        if(e.getEntity() instanceof ServerPlayer p && e.getTarget() instanceof ServerPlayer target){
            var pair=state(p.server).pair(target.getUUID());if(pair!=null) PacketDistributor.sendToPlayer(p,payload(p.server,pair));
        }
    }
    private static void leave(ServerPlayer p,String reason) {
        release(p,reason);var r=state(p.server).cancel(p.getUUID());if(r!=null)notify(p.server,r,"The hand-holding request was cancelled: "+reason+".");clearSpeed(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)leave(p,"a player disconnected");}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)leave(p,"a player changed dimensions");}
    @SubscribeEvent public static void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p)leave(p,"a player died");}
    @SubscribeEvent public static void attack(AttackEntityEvent e){if(e.getEntity() instanceof ServerPlayer p)release(p,"a player attacked");}
    @SubscribeEvent public static void hurt(LivingDamageEvent.Post e){if(e.getEntity() instanceof ServerPlayer p)release(p,"a player was hurt");}
    @SubscribeEvent public static void teleport(EntityTeleportEvent e){if(e.getEntity() instanceof ServerPlayer p)release(p,"a player teleported");}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){STATES.remove(e.getServer());STABILITY.remove(e.getServer());FLIGHT.remove(e.getServer());TETHERS.remove(e.getServer());VERTICAL.remove(e.getServer());}
    @SubscribeEvent public static void commands(RegisterCommandsEvent e) {
        var root=Commands.literal("handholding").executes(c->{tell(c.getSource().getPlayerOrException(),"Look at a player and press H, or use /handholding ask <player>. Commands: release, cancel, toggle, status, grab. OP setup: /handholding config grab true.");return 1;});
        var config=Commands.literal("config").requires(c->c.hasPermission(2))
            .executes(c->{String message=grabConfigStatus()+(c.getSource().getEntity() instanceof ServerPlayer p?" Your access: "+GrabPolicy.message(grabBlock(p)):"");c.getSource().sendSuccess(()->Component.literal(message),false);return 1;});
        config.then(Commands.literal("grab").then(Commands.argument("enabled",com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(c->{boolean value=com.mojang.brigadier.arguments.BoolArgumentType.getBool(c,"enabled");boolean dependency=configureGrab(value);c.getSource().sendSuccess(()->Component.literal("Roleplay grabbing "+(HandInHand.ROLEPLAY_GRAB.get()?"enabled":"disabled")+" and saved. "+(dependency?"Required movement following was enabled too. ":"")+grabConfigStatus()),true);return 1;})));
        config.then(Commands.literal("grabOpOnly").then(Commands.argument("enabled",com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(c->{boolean value=com.mojang.brigadier.arguments.BoolArgumentType.getBool(c,"enabled");HandInHand.GRAB_REQUIRES_OP.set(value);HandInHand.GRAB_REQUIRES_OP.save();c.getSource().sendSuccess(()->Component.literal("Roleplay grab OP-only restriction: "+value+" (saved)."),true);return 1;})));
        config.then(Commands.literal("enabled").then(Commands.argument("enabled",com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(c->{boolean value=com.mojang.brigadier.arguments.BoolArgumentType.getBool(c,"enabled");HandInHand.ENABLED.set(value);HandInHand.ENABLED.save();c.getSource().sendSuccess(()->Component.literal(grabConfigStatus()),true);return 1;})));
        config.then(Commands.literal("movementTether").then(Commands.argument("enabled",com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(c->{boolean value=com.mojang.brigadier.arguments.BoolArgumentType.getBool(c,"enabled");HandInHand.MOVEMENT_TETHER.set(value);HandInHand.MOVEMENT_TETHER.save();c.getSource().sendSuccess(()->Component.literal(grabConfigStatus()),true);return 1;})));
        root.then(config);
        root.then(Commands.literal("ask").then(Commands.argument("player",EntityArgument.player()).executes(c->safe(c.getSource().getPlayerOrException(),()->ask(c.getSource().getPlayerOrException(),EntityArgument.getPlayer(c,"player"))))));
        root.then(Commands.literal("grab").then(Commands.argument("player",EntityArgument.player()).executes(c->safe(c.getSource().getPlayerOrException(),()->grab(c.getSource().getPlayerOrException(),EntityArgument.getPlayer(c,"player"))))));
        for(String action:List.of("accept","decline"))root.then(Commands.literal(action).then(Commands.argument("request",StringArgumentType.word()).executes(c->{var p=c.getSource().getPlayerOrException();return safe(p,()->{UUID token=UUID.fromString(StringArgumentType.getString(c,"request"));if(action.equals("accept"))accept(p,token);else{var r=state(p.server).decline(p.getUUID(),token,now(p.server));notify(p.server,r,"The hand-holding request was declined.");}});} )));
        root.then(Commands.literal("release").executes(c->{var p=c.getSource().getPlayerOrException();release(p,"");var r=state(p.server).cancel(p.getUUID());if(r!=null)notify(p.server,r,"The hand-holding request was cancelled.");return 1;}));
        root.then(Commands.literal("cancel").executes(c->{var p=c.getSource().getPlayerOrException();var r=state(p.server).cancel(p.getUUID());if(r!=null)notify(p.server,r,"The hand-holding request was cancelled.");else tell(p,"You have no pending request.");return 1;}));
        root.then(Commands.literal("toggle").executes(c->{var p=c.getSource().getPlayerOrException();if(p.getTags().contains(OPT_OUT)){p.removeTag(OPT_OUT);tell(p,"Hand-holding requests enabled.");}else{p.addTag(OPT_OUT);leave(p,"requests disabled");tell(p,"Hand-holding requests disabled.");}return 1;}));
        root.then(Commands.literal("status").executes(c->{var p=c.getSource().getPlayerOrException();var pair=state(p.server).pair(p.getUUID());tell(p,pair==null?"You are not holding hands.":"You are holding hands. Use /handholding release to let go.");return 1;}));
        e.getDispatcher().register(root);
    }
    @FunctionalInterface private interface Action {void run() throws Exception;}
    private static int safe(ServerPlayer p,Action action){try{action.run();return 1;}catch(Exception ex){tell(p,ex instanceof IllegalArgumentException?"Invalid request.":ex.getMessage());return 0;}}
}
