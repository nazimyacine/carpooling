package fr.esilv.poolup.cities;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CityService {

    private final CityRepository cityRepository;

    @Transactional(readOnly = true)
    public List<CityResponse> listCities() {
        return cityRepository.findAllByOrderByNameAsc().stream()
                .map(CityResponse::from)
                .toList();
    }
}
