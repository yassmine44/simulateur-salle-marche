package tn.esprit.simulateurbackend.dto.actuarial;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ValidatorFactory;

import static org.junit.jupiter.api.Assertions.*;

class ActuarialDtoValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static PolicyProfileRequest profile(String area, String brand, int driverAge) {
        return new PolicyProfileRequest(driverAge, 100, 2, 7, 5000, area, brand, "Regular", "R11");
    }

    private static <T> Set<String> invalidFields(T bean) {
        return validator.validate(bean).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void shouldAcceptAValidProfile() {
        assertTrue(invalidFields(profile("E", "B12", 20)).isEmpty());
    }

    @Test
    void shouldRejectUnknownCategoriesAndOutOfRangeValues() {
        assertEquals(Set.of("area"), invalidFields(profile("Z", "B12", 20)));
        assertEquals(Set.of("vehicleBrand"), invalidFields(profile("E", "B7", 20)));
        assertEquals(Set.of("driverAge"), invalidFields(profile("E", "B12", 16)));
    }

    @Test
    void shouldValidateNestedProfileOfAQuote() {
        QuoteRequest request = new QuoteRequest(profile("Z", "B12", 20), null, null, null, null);
        assertEquals(Set.of("profile.area"), invalidFields(request));
    }

    @Test
    void shouldApplyQuoteDefaults() {
        QuoteRequest request = new QuoteRequest(profile("E", "B12", 20), null, null, null, null);
        assertEquals(1.0, request.years());
        assertTrue(request.explain());
    }

    @Test
    void shouldRejectRatiosLeavingNothingForClaims() {
        assertEquals(Set.of("ratioSumValid"), invalidFields(PricingAssumptionsDto.of(0.7, 0.3, 0.0)));
        assertTrue(invalidFields(PricingAssumptionsDto.of(0.25, 0.05, 0.03)).isEmpty());
    }

    @Test
    void shouldRejectOutOfRangeHorizonAndTop() {
        QuoteRequest request = new QuoteRequest(profile("E", "B12", 20), 0.0, null, true, 12);
        assertEquals(Set.of("years", "top"), invalidFields(request));
    }
}
