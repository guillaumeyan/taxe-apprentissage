package com.guillaumeyan.taxeapprentissage;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class University {
    
    private String name;

    private Double moneyNeeded;
    
    private List<EnterpriseDonation> enterpriseDonations = new ArrayList<>();

    @Data
    public static class EnterpriseDonation {

        private String name;

        private Double money;
    }
}
