package fr.esilv.poolup.cities;

import java.math.BigDecimal;

public record CityResponse(Long id, String name, BigDecimal latitude, BigDecimal longitude) {

    public static CityResponse from(City city) {
        return new CityResponse(city.getId(), city.getName(), city.getLatitude(), city.getLongitude());
    }
}
