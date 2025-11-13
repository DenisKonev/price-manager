package org.qwep.qweppricemanager.search;

import lombok.*;
import org.qwep.qweppricemanager.rest.dto.BrandArticleDto;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class MultipleSearchDto {
    private List<BrandArticleDto> brandArticles;
    private List<UUID> vendorIds;
}
