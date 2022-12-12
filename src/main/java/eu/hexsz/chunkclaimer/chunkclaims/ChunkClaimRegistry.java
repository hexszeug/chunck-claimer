package eu.hexsz.chunkclaimer.chunkclaims;

import eu.hexsz.chunkclaimer.countries.Country;
import net.minecraft.world.chunk.Chunk;

import java.util.HashMap;

public class ChunkClaimRegistry {
    public static final ChunkClaimRegistry REGISTRY = new ChunkClaimRegistry();

    private HashMap<Chunk, Country> claims = new HashMap<>();

    private ChunkClaimRegistry() {}

    public void claim(Chunk chunk, Country country) {
        claims.put(chunk, country);
    }

    public Country getOwner(Chunk chunk) {
        return claims.get(chunk);
    }

    public void unclaim(Chunk chunk) {
        claims.remove(chunk);
    }
}
