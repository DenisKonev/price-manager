package org.qwep.qweppricemanager.search;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.qwep.qweppricemanager.pricedata.PriceDto;
import org.qwep.qweppricemanager.rest.dto.BrandArticleDto;
import org.qwep.qweppricemanager.rest.dto.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@RestController
@Slf4j
@RequestMapping("/search")
@Validated
public class SearchController {
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/byMultipleBrandAndVendors")
    @Operation(summary = "Поиск позиций по прайс-листам.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Результаты поиска."),
            @ApiResponse(responseCode = "400", description = "Запрос выполнен с ошибками.",
                    content = @Content)})
    public ResponseEntity<Response<HashMap<String, List<PriceDto>>>> search(@RequestBody MultipleSearchDto multipleSearch) {
        try {
//            HashMap<BrandArticleDto, List<BrandArticleDto>> mapWithCrosses = new HashMap<>();
//            multipleSearch.getBrandArticles().parallelStream().forEach(brandArticle -> {
//                try {
//                    List<BrandArticleDto> crosses =
//                            dataApiService.getCrossesBy(brandArticle.getBrand(), brandArticle.getArticle());
//                    mapWithCrosses.put(brandArticle, crosses);
//                } catch (RestClientException exception) {
//                    log.warn("While getting crosses dataАpi returned bad response for brandArticle: {}, exception: {}",
//                            brandArticle, exception.getMessage());
//                    mapWithCrosses.put(brandArticle, List.of());
//                }
//            });
//
//            Set<BrandArticleDto> brandArticleToSearch = new HashSet<>();
//            mapWithCrosses.forEach((brandArticle, crosses) -> {
//                brandArticleToSearch.add(brandArticle);
//                brandArticleToSearch.addAll(crosses);
//            });

//            log.debug("byMultipleBrandAndVendors");
            List<BrandArticleDto> brandArticleDtosWithoutNull =
                    multipleSearch
                            .getBrandArticles()
                            .parallelStream()
                            .filter(brandArticleDto -> brandArticleDto.getBrand() != null
                                    && brandArticleDto.getArticle() != null)
                            .toList();
            multipleSearch.setBrandArticles(brandArticleDtosWithoutNull);

            HashMap<String, List<PriceDto>> vendorIdsToPriceDtos = new HashMap<>();
            multipleSearch.getVendorIds().parallelStream().forEach(vendorID ->
                    vendorIdsToPriceDtos.put(
                            vendorID.toString(),
                            searchService.getPrices(
                                    multipleSearch.getBrandArticles().stream().toList(), Optional.ofNullable(vendorID),
                                    false
                            )
                    ));
            return ResponseEntity
                    .ok(new Response<>(vendorIdsToPriceDtos, true, "Search was successful"));
        } catch (Exception exception) {
            log.error("Can't produce multiple search for vendors: {} with exception: {} and trace: {}",
                    multipleSearch.getVendorIds(), exception.getMessage(), ExceptionUtils.getStackTrace(exception));
            return ResponseEntity
                    .internalServerError()
                    .body(
                            new Response<>(
                                    false,
                                    "Can't search prices because of: " + exception.getMessage()
                            )
                    );
        }
    }
}
