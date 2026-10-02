package fr.esilv.poolup.users;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.esilv.poolup.common.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> ApiException.unauthorized("Compte introuvable."));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Compte introuvable."));

        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());

        String carModel = request.carModel() == null ? null : request.carModel().trim();
        user.setCarModel(carModel == null || carModel.isBlank() ? null : carModel);

        return UserResponse.from(userRepository.save(user));
    }
}
