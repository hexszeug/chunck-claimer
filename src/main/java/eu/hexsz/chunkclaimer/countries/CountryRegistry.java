package eu.hexsz.chunkclaimer.countries;

import com.mojang.authlib.GameProfile;

import java.util.Collection;
import java.util.HashMap;

public class CountryRegistry {
    public static final CountryRegistry REGISTRY = new CountryRegistry();

    private final HashMap<String, Country> countries = new HashMap<>();
    private final HashMap<GameProfile, Country.Citizen> citizens = new HashMap<>();

    private CountryRegistry() {};

    public Country getCountry(String name) {
        return countries.get(name);
    }

    public Collection<Country> getCountries() {
        return countries.values();
    }

    public Country createCountry(String name, GameProfile creator) {
        if (name == null || creator == null) return null;
        Country country = new Country(name, creator);
        countries.put(name, country);
        return country;
    }

    public void changeCountryName(Country country, String newName) {
        if (country == null || newName == null) return;
        countries.remove(country.getName());
        countries.put(newName, country);
        country.setName(newName);
    }

    public void deleteCountry(Country country) {
        if (country == null) return;
        countries.remove(country.getName());
    }

    protected void addCitizen(Country.Citizen citizen) {
        if (citizen == null) return;
        citizens.put(citizen.getGameProfile(), citizen);
    }

    public Country.Citizen getCitizen(GameProfile gameProfile) {
        return citizens.get(gameProfile);
    }

    protected void removeCitizen(Country.Citizen citizen) {
        if (citizen == null || !citizens.containsValue(citizen)) return;
        citizens.remove(citizen.getGameProfile());
    }
}
