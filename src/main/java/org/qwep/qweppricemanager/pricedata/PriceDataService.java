package org.qwep.qweppricemanager.pricedata;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.qwep.qweppricemanager.external.PricedbRepository;
import org.qwep.qweppricemanager.rest.dto.ChangeQuantityItemDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class PriceDataService {

    private final PricedbRepository pricedbRepository;


    public PriceDataService(
            PricedbRepository pricedbRepository) {

        this.pricedbRepository = pricedbRepository;
    }

    public Summary save(List<PriceDto> priceDtos, UUID priceTableRef) throws IllegalArgumentException {
        List<PriceDto> priceDtosValidated = priceDtos.stream().filter(PriceDto::isValid).toList();
        if (priceDtosValidated.isEmpty())
            throw new IllegalArgumentException("No valid priceDate to save");

        String priceTableRefString = priceTableRef.toString();
        Summary summary = new Summary();
        summary.setDeclinedDataRowsCount(priceDtos.size() - priceDtosValidated.size());

        pricedbRepository.createEmptyPriceTable(priceTableRefString);

        return pricedbRepository.saveResult(priceDtos, priceTableRef, summary);
    }

    public void dropPriceTable(UUID priceTabeRef) {
        pricedbRepository.deletePriceTable(priceTabeRef);
    }

    public Boolean isThereAnyItems(List<String> refs) {
        return refs.parallelStream().map(UUID::fromString).anyMatch(pricedbRepository::existsAndHaveRows);
    }

    public List<PriceDto> getPriceDtos(String[] priceTableRefs) {
        return pricedbRepository.getPriceDtos(priceTableRefs);
    }

    @SneakyThrows
    @Transactional
    public void changeItemQuantity(ChangeQuantityItemDto cnhgItemQuantity, UUID ref) {
        long currentQuantity = pricedbRepository.getItemQuantity(ref.toString(), cnhgItemQuantity.getBrand(),
                cnhgItemQuantity.getArticle());
        long quantity = currentQuantity - cnhgItemQuantity.getQuantity();
        if (quantity < 0) throw new IllegalArgumentException("Current quantity less then required quantity");
        if (quantity == 0)
            pricedbRepository.deleteRow(ref.toString(), cnhgItemQuantity.getBrand(), cnhgItemQuantity.getArticle());
        pricedbRepository
                .updateRow(ref.toString(), quantity, cnhgItemQuantity.getBrand(), cnhgItemQuantity.getArticle());
    }
}
