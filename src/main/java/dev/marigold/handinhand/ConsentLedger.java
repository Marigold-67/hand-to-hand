package dev.marigold.handinhand;

import java.util.*;

/** Server-thread-only consent state. No command can create a link without a recipient-owned nonce. */
public final class ConsentLedger {
    public record Request(UUID token, UUID sender, UUID recipient, long expires) {}
    public record Pair(UUID first, UUID second, long started, boolean grab) {
        public Pair(UUID first,UUID second,long started){this(first,second,started,false);}
        public UUID other(UUID id) { return first.equals(id) ? second : first; }
    }
    private long lastSession=Long.MIN_VALUE;
    private long session(long now){return lastSession=Math.max(now,lastSession+1);}
    private final Map<UUID, Request> requests = new HashMap<>();
    private final Map<UUID, Pair> links = new HashMap<>();
    private final Map<UUID, Long> grabBlockedUntil = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    public Request pending(UUID player) {
        return requests.values().stream().filter(r -> r.sender.equals(player) || r.recipient.equals(player)).findFirst().orElse(null);
    }
    public Pair pair(UUID player) { return links.get(player); }
    public Collection<Pair> pairs() { return new HashSet<>(links.values()); }
    public Request request(UUID sender, UUID recipient, long now, long duration, long cooldown) {
        if (sender.equals(recipient)) throw new IllegalStateException("You cannot hold your own hand.");
        if (now < cooldowns.getOrDefault(sender, Long.MIN_VALUE)) throw new IllegalStateException("Please wait before sending another request.");
        if (links.containsKey(sender) || links.containsKey(recipient)) throw new IllegalStateException("One of you is already holding hands.");
        if (pending(sender) != null || pending(recipient) != null) throw new IllegalStateException("One of you already has a pending request.");
        Request r = new Request(UUID.randomUUID(), sender, recipient, now + duration);
        requests.put(r.token, r);
        cooldowns.put(sender, now + cooldown);
        return r;
    }
    public Request validate(UUID actor, UUID token, long now) {
        Request r = requests.get(token);
        if (r == null || !r.recipient.equals(actor)) throw new IllegalStateException("This request is no longer available to you.");
        if (now >= r.expires) throw new IllegalStateException("This request has expired.");
        return r;
    }
    public Pair accept(UUID actor, UUID token, long now) {
        Request r = validate(actor, token, now);
        if (links.containsKey(actor) || links.containsKey(r.sender)) throw new IllegalStateException("One of you is already holding hands.");
        requests.remove(token);
        Pair pair = new Pair(r.sender, r.recipient, session(now));
        links.put(pair.first, pair); links.put(pair.second, pair);
        return pair;
    }
    /** Authorization is evaluated by the authenticated server command, never a client-supplied flag. */
    public Pair grab(UUID actor,UUID target,long now,long cooldown,boolean authorized){
        if(!authorized)throw new IllegalStateException("Roleplay grabbing is disabled or you lack permission.");
        if(actor.equals(target))throw new IllegalStateException("You cannot grab yourself.");
        if(now<cooldowns.getOrDefault(actor,Long.MIN_VALUE)||now<grabBlockedUntil.getOrDefault(actor,Long.MIN_VALUE)||now<grabBlockedUntil.getOrDefault(target,Long.MIN_VALUE))
            throw new IllegalStateException("Please wait before grabbing again.");
        if(links.containsKey(actor)||links.containsKey(target)||pending(actor)!=null||pending(target)!=null)
            throw new IllegalStateException("One of you already has a link or pending request.");
        Pair pair=new Pair(actor,target,session(now),true);links.put(actor,pair);links.put(target,pair);cooldowns.put(actor,now+cooldown);return pair;
    }
    public Pair release(UUID actor,long now,long grabCooldown){
        Pair pair=release(actor);
        if(pair!=null&&pair.grab()){grabBlockedUntil.put(pair.first(),now+grabCooldown);grabBlockedUntil.put(pair.second(),now+grabCooldown);}
        return pair;
    }
    public Request decline(UUID actor, UUID token, long now) {
        Request r = validate(actor, token, now); requests.remove(token); return r;
    }
    public Request cancel(UUID actor) {
        Request r = pending(actor); if (r != null) requests.remove(r.token); return r;
    }
    public Pair release(UUID actor) {
        Pair p = links.remove(actor);
        if (p != null) { links.remove(p.first); links.remove(p.second); }
        return p;
    }
    public List<Request> expire(long now) {
        List<Request> expired = new ArrayList<>();
        requests.values().removeIf(r -> { if (now >= r.expires) { expired.add(r); return true; } return false; });
        grabBlockedUntil.entrySet().removeIf(e -> now >= e.getValue());
        cooldowns.entrySet().removeIf(e -> now >= e.getValue());
        return expired;
    }
}
