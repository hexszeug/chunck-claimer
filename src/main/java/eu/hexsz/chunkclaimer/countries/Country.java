package eu.hexsz.chunkclaimer.countries;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.StringIdentifiable;

import java.util.*;

public class Country {
    private String name;
    private Text displayName;
    private Text description;
    //TODO power
    private final ArrayList<Citizen> citizens = new ArrayList<>();
    private final ArrayList<GameProfile> invitations = new ArrayList<>();

    protected Country (String name, GameProfile creator) {
        if (name == null) throw new IllegalArgumentException("Country name cannot be null");
        if (creator == null) throw new IllegalArgumentException("Country creator cannot be null");
        this.name = name;

        Citizen owner = addAsCitizen(creator);
        for (CitizenPermission value : CitizenPermission.values()) {
            owner.setPermission(value, true);
        }
    }

    public String getName() {
        return name;
    }

    protected void setName(String name) {
        this.name = name;
    }

    public Text getDisplayName() {
        return displayName;
    }

    public void setDisplayName(Text displayName) {
        //TODO make hover-able display name
        this.displayName = displayName;
    }

    public Text getDescription() {
        return description;
    }

    public void setDescription(Text description) {
        this.description = description;
    }

    public Collection<Citizen> getCitizens() {
        return citizens.stream().toList();
    }

    public Citizen addAsCitizen(GameProfile gameProfile) {
        if (gameProfile == null) return null;
        Citizen citizen = new Citizen(gameProfile);
        this.citizens.add(citizen);
        CountryRegistry.REGISTRY.addCitizen(citizen);
        return citizen;
    }

    public void removeCitizen(Citizen citizen) {
        citizens.removeIf(x -> x == citizen);
        CountryRegistry.REGISTRY.removeCitizen(citizen);
    }

    public void invite(GameProfile gameProfile) {
        if (gameProfile == null) return;
        invitations.add(gameProfile);
    }

    public boolean isInvited(GameProfile gameProfile) {
        if (gameProfile == null) return false;
        return invitations.contains(gameProfile);
    }

    public Collection<GameProfile> getInvitations() {
        return invitations.stream().toList();
    }

    public void uninvite(GameProfile gameProfile) {
        if (gameProfile == null) return;
        invitations.remove(gameProfile);
    }

    public void broadcast(Text message, MinecraftServer server) {
        citizens.forEach(citizen -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(citizen.getGameProfile().getId());
            if (player != null) player.sendMessage(message);
        });
    }

    public enum CitizenPermission {
        CAN_CHANGE_PERMISSION("changePermission"),
        CAN_DELETE("delete"),
        CAN_RENAME("rename"),
        CAN_CLAIM("claim"),
        CAN_KICK("kick"),
        CAN_INVITE("invite");

        private final String name;

        CitizenPermission(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    public class Citizen {
        private final GameProfile gameProfile;

        private HashMap<CitizenPermission, Boolean> permissions;

        private Citizen(GameProfile gameProfile) {
            this.gameProfile = gameProfile;
            permissions = new HashMap<>();
            for (CitizenPermission value : CitizenPermission.values()) {
                permissions.put(value, false);
            }
        }

        public GameProfile getGameProfile() {
            return gameProfile;
        }

        public Country getCountry() {
            return Country.this;
        }

        public boolean hasPermission(CitizenPermission permission) {
            return permissions.get(permission);
        }

        public void setPermission(CitizenPermission permission, boolean value) {
            permissions.put(permission, value);
        }
    }
}
