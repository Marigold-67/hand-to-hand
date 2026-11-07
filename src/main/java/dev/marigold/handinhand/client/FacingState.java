package dev.marigold.handinhand.client;
import dev.marigold.handinhand.*;
import net.minecraft.client.Minecraft;
import java.util.*;
/** Cache uses the local client's clock for every entity, including observers. */
final class FacingState {
    static final Map<UUID,Entry> frames=new HashMap<>();
    static final Map<UUID,FacingPayload> newest=new HashMap<>();
    static final class Entry {
        FacingPayload packet;int receivedTick;final Formation display;double previous;
        Entry(FacingPayload p,int tick,double initial){packet=p;receivedTick=tick;display=new Formation(initial,1);previous=initial;}
        boolean grab(){return packet.grab();}
        int receivedTick(){return receivedTick;}
    }
    static void receive(FacingPayload p){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||!Double.isFinite(p.heading())||p.first().equals(p.second()))return;
        var link=HandClient.LINKS.get(p.follower());
        if(link==null||link.started()!=p.started()||!link.first().equals(p.first())||!link.second().equals(p.second()))return;
        if(!p.follower().equals(p.first())&&!p.follower().equals(p.second()))return;
        var last=newest.get(p.first());
        if(last!=null&&last.started()==p.started()&&last.frameTick()>p.frameTick())return;
        newest.put(p.first(),p);
        if(!p.assisted()){frames.remove(p.first());frames.remove(p.second());return;}
        UUID leader=p.follower().equals(p.first())?p.second():p.first();frames.remove(leader);
        var old=frames.get(p.follower());
        if(old!=null&&old.packet.started()==p.started()&&old.packet.frameTick()>p.frameTick())return;
        if(old==null||old.packet.started()!=p.started()){
            var entity=mc.level.getPlayerByUUID(p.follower());double initial=entity==null?p.heading():Math.toRadians(entity.yBodyRot);
            old=new Entry(p,mc.player.tickCount,initial);frames.put(p.follower(),old);
        }
        old.packet=p;old.receivedTick=mc.player.tickCount;
    }
    static Entry forPlayer(UUID id,long started){var f=frames.get(id);return f!=null&&f.packet.started()==started?f:null;}
    static boolean isGrabTarget(UUID id,long started){var mc=Minecraft.getInstance();var f=forPlayer(id,started);return f!=null&&f.grab()&&mc.player!=null&&mc.player.tickCount-f.receivedTick<=6;}
    static void tick(){var mc=Minecraft.getInstance();if(mc.player==null){clear();return;}
        frames.values().removeIf(f->mc.player.tickCount-f.receivedTick>6);
        for(var f:frames.values()){f.previous=f.display.heading();f.display.turnToward(f.packet.heading(),.20);}
    }
    static void remove(UUID a,UUID b){frames.remove(a);frames.remove(b);newest.remove(a);newest.remove(b);}
    static void clear(){frames.clear();newest.clear();}
}
