package com.pictoglyph.pictoglyphapi.config;

import com.pictoglyph.pictoglyphapi.entities.core.Language;
import com.pictoglyph.pictoglyphapi.entities.core.LanguagePlace;
import com.pictoglyph.pictoglyphapi.entities.core.Place;
import com.pictoglyph.pictoglyphapi.repositories.core.LanguagePlaceRepository;
import com.pictoglyph.pictoglyphapi.repositories.core.LanguageRepository;
import com.pictoglyph.pictoglyphapi.repositories.core.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pictoglyph.seed.enabled", havingValue = "true")
public class DevelopmentDataSeeder implements ApplicationRunner {

	private final LanguageRepository languageRepository;
	private final PlaceRepository placeRepository;
	private final LanguagePlaceRepository languagePlaceRepository;

	@Override
	public void run(ApplicationArguments args) {
		seedLanguages();
		seedPlaces();
		seedLanguagePlaces();
	}

	private void seedLanguages() {
		List<Language> languages = List.of(
				Language.builder()
						.name("Ancient Egyptian")
						.scriptName("Egyptian hieroglyphs")
						.dateStart(-3200)
						.dateEnd(400)
						.build(),

				Language.builder()
						.name("Latin")
						.scriptName("Latin alphabet")
						.dateStart(-700)
						.dateEnd(null)
						.build(),

				Language.builder()
						.name("Classical Chinese")
						.scriptName("Hanzi")
						.dateStart(-1200)
						.dateEnd(null)
						.build(),

				Language.builder()
						.name("Japanese")
						.scriptName("Kanji and Kana")
						.dateStart(700)
						.dateEnd(null)
						.build(),

				Language.builder()
						.name("Maya")
						.scriptName("Maya glyphs")
						.dateStart(-300)
						.dateEnd(1700)
						.build()
		);

		for (Language language : languages) {
			if (!languageRepository.existsByNameIgnoreCase(language.getName())) {
				languageRepository.save(language);
			}
		}
	}

	private void seedPlaces() {
		List<Place> places = List.of(
				Place.builder()
						.name("Memphis")
						.country("Egypt")
						.latitude(new BigDecimal("29.8440"))
						.longitude(new BigDecimal("31.2510"))
						.build(),

				Place.builder()
						.name("Rome")
						.country("Italy")
						.latitude(new BigDecimal("41.9028"))
						.longitude(new BigDecimal("12.4964"))
						.build(),

				Place.builder()
						.name("Xi'an")
						.country("China")
						.latitude(new BigDecimal("34.3416"))
						.longitude(new BigDecimal("108.9398"))
						.build(),

				Place.builder()
						.name("Nara")
						.country("Japan")
						.latitude(new BigDecimal("34.6851"))
						.longitude(new BigDecimal("135.8048"))
						.build(),

				Place.builder()
						.name("Tikal")
						.country("Guatemala")
						.latitude(new BigDecimal("17.2220"))
						.longitude(new BigDecimal("-89.6237"))
						.build()
		);

		for (Place place : places) {
			if (!placeRepository.existsByNameIgnoreCaseAndCountryIgnoreCase(place.getName(), place.getCountry())) {
				placeRepository.save(place);
			}
		}
	}

	private void seedLanguagePlaces() {
		createLanguagePlace("Ancient Egyptian", "Memphis", "Egypt", -3200, 400, 90);
		createLanguagePlace("Latin", "Rome", "Italy", -700, null, 85);
		createLanguagePlace("Classical Chinese", "Xi'an", "China", -1200, null, 80);
		createLanguagePlace("Japanese", "Nara", "Japan", 700, null, 75);
		createLanguagePlace("Maya", "Tikal", "Guatemala", -300, 1700, 80);
	}

	private void createLanguagePlace(
			String languageName,
			String placeName,
			String country,
			Integer dateStart,
			Integer dateEnd,
			Integer confidence
	) {
		Language language = languageRepository.findByNameIgnoreCase(languageName)
				.orElseThrow(() -> new IllegalStateException("Missing seeded language: " + languageName));

		Place place = placeRepository.findByNameIgnoreCaseAndCountryIgnoreCase(placeName, country)
				.orElseThrow(() -> new IllegalStateException("Missing seeded place: " + placeName + ", " + country));

		if (languagePlaceRepository.existsByLanguage_IdAndPlace_Id(language.getId(), place.getId())) {
			return;
		}

		LanguagePlace languagePlace = LanguagePlace.builder()
				.language(language)
				.place(place)
				.dateStart(dateStart)
				.dateEnd(dateEnd)
				.confidence(confidence)
				.build();

		languagePlaceRepository.save(languagePlace);
	}
}