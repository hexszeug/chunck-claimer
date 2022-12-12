package eu.hexsz.chunkclaimer.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import eu.hexsz.chunkclaimer.chunkclaims.ChunkClaimRegistry;
import eu.hexsz.chunkclaimer.countries.Country;
import eu.hexsz.chunkclaimer.countries.CountryRegistry;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EnumArgumentType;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.command.argument.TextArgumentType;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.world.chunk.Chunk;

import java.security.Permission;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static net.minecraft.server.command.CommandManager.*;

public class CountryCommand {
    private static final SimpleCommandExceptionType CREATE_DUPLICATE_EXCEPTION = new SimpleCommandExceptionType(Text.literal("A country with that name already exists"));
    private static final SimpleCommandExceptionType NOT_INVITED_EXCEPTION = new SimpleCommandExceptionType(Text.literal("You are not invited to this country"));
    private static final SimpleCommandExceptionType JOIN_MULTIPLE_EXCEPTION = new SimpleCommandExceptionType(Text.literal("You cannot join multiple countries"));
    private static final SimpleCommandExceptionType PERMISSION_VIOLATION_EXCEPTION = new SimpleCommandExceptionType(Text.literal("You don't have the permissions to perform this action"));
    private static final SimpleCommandExceptionType NON_CITIZEN_EXCEPTION = new SimpleCommandExceptionType(Text.literal("You are citizen of no country"));
    private static final SimpleCommandExceptionType COUNTRY_NOT_FOUND_EXCEPTION = new SimpleCommandExceptionType(Text.literal("Country does not exist"));
    private static final SimpleCommandExceptionType ALREADY_INVITED_JOINED_EXCEPTION = new SimpleCommandExceptionType(Text.literal("The player/s is/are already invited or member of the country"));
    private static final SimpleCommandExceptionType NON_INVITED_EXCEPTION = new SimpleCommandExceptionType(Text.literal("The player/s is/are not invited to the country"));
    private static final SimpleCommandExceptionType EMPTY_SELECTOR_EXCEPTION = new SimpleCommandExceptionType(Text.literal("Your selection targets no players"));
    private static final SimpleCommandExceptionType SELECT_SINGLE_EXCEPTION = new SimpleCommandExceptionType(Text.literal("You can only select a single player"));
    private static final SimpleCommandExceptionType PLAYER_NOT_CITIZEN_EXCEPTION = new SimpleCommandExceptionType(Text.literal("The player is not in your country"));
    private static final SimpleCommandExceptionType INVALID_PERMISSION_EXCEPTION = new SimpleCommandExceptionType(Text.literal("The permission does not exist"));
    private static final SimpleCommandExceptionType CHUNK_OCCUPIED_EXCEPTION = new SimpleCommandExceptionType(Text.literal("Your country is too weak to claim this chunk"));

    public static void register(
            CommandDispatcher<ServerCommandSource> dispatcher,
            CommandRegistryAccess commandRegistryAccess,
            RegistrationEnvironment registrationEnvironment
    ) {
        dispatcher.register(literal(
                "country"
        ).then(literal("adminize").requires(source -> source.hasPermissionLevel(4)).then(argument("admins", GameProfileArgumentType.gameProfile()).executes(
                context -> executeAdminize(context.getSource(), GameProfileArgumentType.getProfileArgument(context, "admins"))
        ))).then(literal("create").then(argument("name", StringArgumentType.word()).executes(
                context -> executeCreate(context.getSource(), StringArgumentType.getString(context, "name"))
        ).then(argument("displayName", TextArgumentType.text()).executes(
                context -> executeCreate(context.getSource(), StringArgumentType.getString(context, "name"), TextArgumentType.getTextArgument(context, "displayName"))
        )))).then(literal("list").executes(
                context -> executeListCitizen(context.getSource())
        ).then(argument("country", StringArgumentType.word()).suggests(COUNTRY_NAMES_SUGGESTION_PROVIDER).executes(
                context -> executeListCitizen(context.getSource(), StringArgumentType.getString(context, "country"))
        ))).then(literal("invite").then(argument("players", GameProfileArgumentType.gameProfile()).executes(
                context -> executeInvite(context.getSource(), GameProfileArgumentType.getProfileArgument(context, "players"))
        ))).then(literal("uninvite").then(argument("players", GameProfileArgumentType.gameProfile()).executes(
                context -> executeUninvite(context.getSource(), GameProfileArgumentType.getProfileArgument(context, "players"))
        ))).then(literal("join").then(argument("country", StringArgumentType.word()).suggests(INVITED_COUNTRY_NAMES_SUGGESTION_PROVIDER).executes(
                context -> executeJoin(context.getSource(), StringArgumentType.getString(context, "country"))
        ))).then(literal("leave").executes(
                context -> executeLeave(context.getSource())
        )).then(literal("delete").executes(
                context -> executeDelete(context.getSource())
        )).then(literal("permission").then(argument("player", GameProfileArgumentType.gameProfile()).executes(
                context -> executeShowPermissions(context.getSource(), GameProfileArgumentType.getProfileArgument(context, "player"))
        ).then(argument("permission", StringArgumentType.word()).suggests(PERMISSIONS_SUGGESTION_PROVIDER).executes(
                context -> executeShowPermission(context.getSource(), GameProfileArgumentType.getProfileArgument(context, "player"), parsePermission(StringArgumentType.getString(context, "permission")))
        ).then(argument("value", BoolArgumentType.bool()).executes(
                context -> executeChangePermission(context.getSource(), GameProfileArgumentType.getProfileArgument(context, "player"), parsePermission(StringArgumentType.getString(context, "permission")), BoolArgumentType.getBool(context, "value"))
        ))))).then(literal("claim").executes(
                context -> executeClaim(context.getSource())
        )));
    }

    //TODO make permission system cleaner

    private static int executeClaim(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Country.Citizen citizen = getCitizen(source);
        if (!citizen.hasPermission(Country.CitizenPermission.CAN_CLAIM)) throw PERMISSION_VIOLATION_EXCEPTION.create();
        ChunkClaimRegistry registry = ChunkClaimRegistry.REGISTRY;
        Chunk chunk = player.getWorld().getChunk(player.getBlockPos());
        if (registry.getOwner(chunk) != null) throw CHUNK_OCCUPIED_EXCEPTION.create(); //TODO add power to claim process
        ChunkClaimRegistry.REGISTRY.claim(player.getWorld().getChunk(player.getBlockPos()), citizen.getCountry());
        return 1; //TODO return power
    }

    private static int executeChangePermission(ServerCommandSource source, Collection<GameProfile> gameProfiles, Country.CitizenPermission permission, boolean value) throws CommandSyntaxException {
        Country.Citizen citizenMe = getCitizen(source);
        if (!citizenMe.hasPermission(Country.CitizenPermission.CAN_CHANGE_PERMISSION)) throw PERMISSION_VIOLATION_EXCEPTION.create();
        Country.Citizen citizen = parseSingleCitizen(gameProfiles);
        if (citizen.hasPermission(permission) == value) {
            source.sendFeedback(
                    Text.literal("Nothing changed"),
                    false
            );
            return -1;
        }
        citizen.setPermission(permission, value);
        source.sendFeedback(
                Text.literal("Changed " + permission + " of " + citizen.getGameProfile().getName() + " to " + value),
                false
        );
        return value ? 1 : 0;
    }

    private static int executeShowPermission(ServerCommandSource source, Collection<GameProfile> gameProfiles, Country.CitizenPermission permission) throws CommandSyntaxException {
        Country.Citizen citizenMe = getCitizen(source);
        Country.Citizen citizen = parseSingleCitizen(gameProfiles);
        if (!citizenMe.hasPermission(Country.CitizenPermission.CAN_CHANGE_PERMISSION) && citizenMe != citizen) throw PERMISSION_VIOLATION_EXCEPTION.create();
        source.sendFeedback(
                Text.literal(
                        citizenMe.getGameProfile().getName()
                        + " ("
                        + permission.toString()
                        + "): "
                        + citizen.hasPermission(permission)
                ),
                false
        );
        return citizen.hasPermission(permission) ? 1 : 0;
    }

    private static int executeShowPermissions(ServerCommandSource source, Collection<GameProfile> gameProfiles) throws CommandSyntaxException {
        Country.Citizen citizenMe = getCitizen(source);
        Country.Citizen citizen = parseSingleCitizen(gameProfiles);
        if (!citizenMe.hasPermission(Country.CitizenPermission.CAN_CHANGE_PERMISSION) && citizenMe != citizen) throw PERMISSION_VIOLATION_EXCEPTION.create();
        Collection<Country.CitizenPermission> permissions = Arrays.stream(Country.CitizenPermission.values()).filter(citizen::hasPermission).toList();
        source.sendFeedback(
                Text.literal(citizen.getGameProfile().getName() + " has the following permissions: \n")
                        .append(Texts.join(
                                permissions.stream()
                                        .map(Country.CitizenPermission::toString)
                                        .map(Text::literal)
                                        .toList(),
                                Text.literal("\n")
                        )),
                false
        );
        return permissions.size();
    }

    private static int executeAdminize(ServerCommandSource source, Collection<GameProfile> gameProfiles) {
        Collection<Country.Citizen> admins = gameProfiles.stream()
                .map(CountryRegistry.REGISTRY::getCitizen)
                .filter(Objects::nonNull)
                .toList();
        admins.forEach(citizen -> citizen.setPermission(Country.CitizenPermission.CAN_CHANGE_PERMISSION, true));
        source.sendFeedback(
                Text.literal("Adminized the following player(s): ")
                        .append(Texts.joinOrdered(admins
                                .stream()
                                .map(Country.Citizen::getGameProfile)
                                .map(GameProfile::getName)
                                .toList()
                        )),
                false
        );
        return admins.size();
    }

    private static int executeDelete(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Country.Citizen citizen = getCitizen(source);
        if (!citizen.hasPermission(Country.CitizenPermission.CAN_DELETE)) throw PERMISSION_VIOLATION_EXCEPTION.create();
        Country country = citizen.getCountry();
        country.broadcast(
                Text
                        .literal("Your country ")
                        .append(country.getDisplayName())
                        .append(Text.literal(" was deleted by "))
                        .append(player.getName()),
                source.getServer()
        );
        Collection<Country.Citizen> citizens = country.getCitizens();
        citizens.forEach(country::removeCitizen);
        CountryRegistry.REGISTRY.deleteCountry(country);
        source.sendFeedback(Text.literal("Country deleted"), false);
        return citizens.size();
    }

    private static int executeLeave(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Country.Citizen citizen = getCitizen(source);
        Country country = citizen.getCountry();
        country.removeCitizen(citizen);
        country.broadcast(Text.empty().append(player.getName()).append(" left the country"), source.getServer());
        if (country.getCitizens().isEmpty()) CountryRegistry.REGISTRY.deleteCountry(country);
        source.sendFeedback(Text.literal("You left the country ").append(country.getDisplayName()), false);
        return 1;
    }

    private static int executeInvite(ServerCommandSource source, Collection<GameProfile> gameProfiles) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        CountryRegistry registry = CountryRegistry.REGISTRY;
        Country.Citizen citizen = getCitizen(source);
        if (gameProfiles.isEmpty()) throw EMPTY_SELECTOR_EXCEPTION.create();
        if (!citizen.hasPermission(Country.CitizenPermission.CAN_INVITE)) throw PERMISSION_VIOLATION_EXCEPTION.create();
        Country country = citizen.getCountry();
        PlayerManager playerManager = source.getServer().getPlayerManager();
        Collection<GameProfile> invitations = gameProfiles.stream().filter(gameProfile -> {
            Country.Citizen c;
            if ((c = registry.getCitizen(gameProfile)) != null && c.getCountry() == country) return false;
            return !country.isInvited(gameProfile);
        }).toList();
        invitations.forEach(gameProfile -> {
            country.invite(gameProfile);
            ServerPlayerEntity invitation = playerManager.getPlayer(gameProfile.getId());
            if (invitation == null) return;
            invitation.sendMessage(Text
                    .literal("You were invited to join the country ")
                    .append(country.getDisplayName())
                    .append(Text.literal(" by "))
                    .append(player.getName())
            );
        });
        if (invitations.isEmpty()) throw ALREADY_INVITED_JOINED_EXCEPTION.create();
        source.sendFeedback(
                Text.literal("You invited the following player(s) to join ")
                        .append(country.getDisplayName())
                        .append(": ")
                        .append(Texts.joinOrdered(invitations.stream().map(GameProfile::getName).toList())),
                false
        );
        return invitations.size();
    }

    private static int executeUninvite(ServerCommandSource source, Collection<GameProfile> gameProfiles) throws CommandSyntaxException {
        Country.Citizen citizen = getCitizen(source);
        if (gameProfiles.isEmpty()) throw EMPTY_SELECTOR_EXCEPTION.create();
        if (!citizen.hasPermission(Country.CitizenPermission.CAN_INVITE)) throw PERMISSION_VIOLATION_EXCEPTION.create();
        Country country = citizen.getCountry();
        Collection<GameProfile> invitations = gameProfiles.stream()
                .filter(country::isInvited)
                .toList();
        invitations.forEach(country::uninvite);
        if (invitations.isEmpty()) throw NON_INVITED_EXCEPTION.create();
        source.sendFeedback(
                Text.literal("You uninvited the following player(s): ")
                        .append(Texts.joinOrdered(invitations.stream().map(GameProfile::getName).toList())),
                false
        );
        return invitations.size();
    }

    private static int executeJoin(ServerCommandSource source, String countryName) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        CountryRegistry registry = CountryRegistry.REGISTRY;
        Country country;
        if ((country = registry.getCountry(countryName)) == null) throw COUNTRY_NOT_FOUND_EXCEPTION.create();
        if (!country.isInvited(player.getGameProfile())) throw NOT_INVITED_EXCEPTION.create();
        if (registry.getCitizen(player.getGameProfile()) != null) throw JOIN_MULTIPLE_EXCEPTION.create();
        country.broadcast(Text.empty().append(player.getName()).append(Text.literal(" joined the country")), source.getServer());
        country.addAsCitizen(player.getGameProfile());
        source.sendFeedback(Text.literal("Joined country ").append(country.getDisplayName()), false);
        return 1;
    }

    private static int executeCreate(ServerCommandSource source, String name) throws CommandSyntaxException {
        return executeCreate(source, name, Text.literal(name));
    }

    private static int executeCreate(ServerCommandSource source, String name, Text displayName) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        CountryRegistry registry = CountryRegistry.REGISTRY;
        if (registry.getCountry(name) != null) throw CREATE_DUPLICATE_EXCEPTION.create();
        if (registry.getCitizen(player.getGameProfile()) != null) throw JOIN_MULTIPLE_EXCEPTION.create();
        Country country = registry.createCountry(name, player.getGameProfile());
        country.setDisplayName(displayName);
        country.setDescription(Text.literal("A country named ").append(displayName));
        source.sendFeedback(Text.literal("Created country ").append(displayName), false);
        return 1;
    }

    private static int executeListCitizen(ServerCommandSource source) throws CommandSyntaxException {
        Country.Citizen citizen = getCitizen(source);
        return executeListCitizen(source, citizen.getCountry());
    }

    private static int executeListCitizen(ServerCommandSource source, String name) throws CommandSyntaxException {
        Country country;
        if ((country = CountryRegistry.REGISTRY.getCountry(name)) == null) throw COUNTRY_NOT_FOUND_EXCEPTION.create();
        return executeListCitizen(source, country);
    }

    private static int executeListCitizen(ServerCommandSource source, Country country) throws CommandSyntaxException {
        Collection<Country.Citizen> citizens = country.getCitizens();
        source.sendFeedback(
                Text.literal("Citizens of ")
                        .append(country.getDisplayName())
                        .append(Text.literal(": "))
                        .append(Texts.joinOrdered(citizens.stream().map(Country.Citizen::getGameProfile).map(GameProfile::getName).toList())),
                false
        );
        return citizens.size();
    }

    private static Country.Citizen getCitizen(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Country.Citizen citizen;
        if ((citizen = CountryRegistry.REGISTRY.getCitizen(player.getGameProfile())) == null) throw NON_CITIZEN_EXCEPTION.create();
        return citizen;
    }

    private static Country.Citizen parseSingleCitizen(Collection<GameProfile> gameProfiles) throws CommandSyntaxException {
        if (gameProfiles.size() != 1) throw SELECT_SINGLE_EXCEPTION.create();
        Country.Citizen citizen;
        if ((citizen = CountryRegistry.REGISTRY.getCitizen(gameProfiles.stream().toList().get(0))) == null) throw PLAYER_NOT_CITIZEN_EXCEPTION.create();
        return citizen;
    }

    private static Country.CitizenPermission parsePermission(String string) throws CommandSyntaxException {
        for (Country.CitizenPermission permission : Country.CitizenPermission.values()) {
            if (permission.toString().equals(string)) return permission;
        }
        throw INVALID_PERMISSION_EXCEPTION.create();
    }

    private static final SuggestionProvider<ServerCommandSource> COUNTRY_NAMES_SUGGESTION_PROVIDER = (context, builder) -> {
        CountryRegistry.REGISTRY.getCountries().stream().map(Country::getName).forEach(builder::suggest);
        return builder.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> INVITED_COUNTRY_NAMES_SUGGESTION_PROVIDER = (context, builder) -> {
        GameProfile gameProfile = context.getSource().getPlayerOrThrow().getGameProfile();
        CountryRegistry.REGISTRY.getCountries().stream().filter(country -> country.isInvited(gameProfile)).map(Country::getName).forEach(builder::suggest);
        return builder.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> PERMISSIONS_SUGGESTION_PROVIDER = (context, builder) -> {
        Arrays.stream(Country.CitizenPermission.values()).map(Country.CitizenPermission::toString).forEach(builder::suggest);
        return builder.buildFuture();
    };

    //TODO better suggestions for game profiles
}
