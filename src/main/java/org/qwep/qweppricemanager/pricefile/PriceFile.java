package org.qwep.qweppricemanager.pricefile;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.internal.util.stereotypes.Lazy;
import org.qwep.qweppricemanager.pricesender.PriceSenderInfoEntity;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "price_file", catalog = "qwep_price_dev")
public class PriceFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    private String name;

    @OneToOne(cascade = CascadeType.MERGE)
    @JoinColumn(name = "sender_id", referencedColumnName = "id")
    private PriceSenderInfoEntity priceSenderInfoEntity;
    @Lazy
    private byte[] file;

    public PriceFile(String name, PriceSenderInfoEntity priceSenderInfoEntity, byte[] file) {
        this.name = name;
        this.priceSenderInfoEntity = priceSenderInfoEntity;
        this.file = file;
    }
}
