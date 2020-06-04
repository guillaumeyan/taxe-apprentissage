package com.guillaumeyan.taxeapprentissage;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
@RequiredArgsConstructor
@Slf4j
public class MyService {

    private final ResourceLoader resourceLoader;

    @Value("${input-file}")
    private String inputFile;

    @Value("${output-file}")
    private String outputFile;

    private final List<University> universitiesResult = new ArrayList<>();

    @PostConstruct
    public void buildTaxeApprentissage() throws IOException {
        log.info("input file come from {}", inputFile);
        log.info("output file will be generated in {}", outputFile);
        Resource resource;
        if(Paths.get(inputFile).isAbsolute()) {
            resource = resourceLoader.getResource("file:" + inputFile);
        } else {
            resource = resourceLoader.getResource("classpath:" + inputFile);
        }
        FileInputStream excelFile = new FileInputStream(resource.getFile());
        try (Workbook workbook = new XSSFWorkbook(excelFile)) {
            Sheet informationSheet = workbook.getSheetAt(0);
            List<EnterpriseExcel> enterprises = getEnterprises(informationSheet);
            List<UniversityExcel> universities = getUniversitiesResult(informationSheet);
            Assert.isTrue(!enterprises.isEmpty(), "Au moins une entreprise attendue");
            Assert.isTrue(!universities.isEmpty(), "Au moins une université attendue");
            applyTaxeApprentissage(enterprises, universities, workbook.getSheetAt(1));
            workbook.getSheetAt(1).autoSizeColumn(0);
            workbook.getSheetAt(1).autoSizeColumn(1);
            workbook.getSheetAt(1).autoSizeColumn(2);
            workbook.getSheetAt(1).autoSizeColumn(3);
            workbook.getSheetAt(1).autoSizeColumn(4);
            workbook.getSheetAt(1).autoSizeColumn(5);
            workbook.getSheetAt(1).autoSizeColumn(6);
            workbook.getSheetAt(1).autoSizeColumn(7);
            try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                workbook.write(outputStream);
            }
        }
    }

    private List<EnterpriseExcel> getEnterprises(Sheet datatypeSheet) {
        List<EnterpriseExcel> enterprises = new ArrayList<>();
        for (int i = 1; i < datatypeSheet.getRow(0).getLastCellNum()-1; i++) {
            String name = datatypeSheet.getRow(0).getCell(i).getStringCellValue();
            double money = datatypeSheet.getRow(1).getCell(i).getNumericCellValue();
            EnterpriseExcel enterpriseExcel = new EnterpriseExcel(name, money);
            enterprises.add(enterpriseExcel);
        }
        return enterprises;
    }

    private List<UniversityExcel> getUniversitiesResult(Sheet datatypeSheet) {
        List<UniversityExcel> universities = new ArrayList<>();
        for (int i = 1; i < datatypeSheet.getRow(3).getLastCellNum()-1; i++) {
            String name = datatypeSheet.getRow(3).getCell(i).getStringCellValue();
            double moneyToGet = datatypeSheet.getRow(4).getCell(i).getNumericCellValue();
            UniversityExcel universityExcel = new UniversityExcel(name, moneyToGet);
            universities.add(universityExcel);
        }
        return universities;
    }

    private void applyTaxeApprentissage(List<EnterpriseExcel> enterprises, List<UniversityExcel> universities, Sheet sheetToWriteResult) throws IOException {
        Iterator<UniversityExcel> iterator = universities.iterator();
        UniversityExcel universityExcel = iterator.next();
        University university = buildUniversityDonation(universityExcel);
        boolean hasNextUniversity = true;
        for (EnterpriseExcel enterprise : enterprises) {
            while(enterprise.getMoney() != 0D && hasNextUniversity) {
                University.EnterpriseDonation enterpriseDonation = new University.EnterpriseDonation();
                enterpriseDonation.setName(enterprise.getName());
                university.getEnterpriseDonations().add(enterpriseDonation);
                double moneyLeftForEnterprise = enterprise.getMoney() - university.getMoneyNeeded();
                if(moneyLeftForEnterprise > 0) {
                    // enterprise have more money than university
                    enterpriseDonation.setMoney(university.getMoneyNeeded()); // enterprise gave everything needed
                    university.setMoneyNeeded(0D);
                    enterprise.setMoney(moneyLeftForEnterprise);
                    if(iterator.hasNext()) {
                        universityExcel = iterator.next();
                        university = buildUniversityDonation(universityExcel);
                    } else {
                        hasNextUniversity = false;
                    }
                } else if (moneyLeftForEnterprise <= 0) {
                    // university need more money $$$
                    // go to next enterprise
                    university.setMoneyNeeded(university.getMoneyNeeded() - enterprise.getMoney());
                    enterpriseDonation.setMoney(enterprise.getMoney());
                    enterprise.setMoney(0D);
                    // go to next university
                    if(moneyLeftForEnterprise == 0) {
                        if (iterator.hasNext()) {
                            universityExcel = iterator.next();
                            university = buildUniversityDonation(universityExcel);
                        } else {
                            hasNextUniversity = false;
                        }
                    }
                }
            }
        }
        writeResultInExcel(sheetToWriteResult);
    }

    private University buildUniversityDonation(UniversityExcel universityExcel) {
        University university = new University();
        university.setName(universityExcel.getName());
        university.setMoneyNeeded(universityExcel.getMoneyToGet());
        universitiesResult.add(university);
        return university;
    }

    private void writeResultInExcel(Sheet sheetToWriteResult) {
        int indexRowUniversity = 1;
        for (University university : universitiesResult) {
            sheetToWriteResult.createRow(indexRowUniversity).createCell(0).setCellValue(university.getName());
            sheetToWriteResult.createRow(indexRowUniversity + 1).createCell(0).setCellValue(university.getMoneyNeeded());
            for (int j = 0; j < university.getEnterpriseDonations().size(); j++) {
                sheetToWriteResult.getRow(indexRowUniversity).createCell(j + 1).setCellValue(university.getEnterpriseDonations().get(j).getName());
                sheetToWriteResult.getRow(indexRowUniversity + 1).createCell(j + 1).setCellValue(university.getEnterpriseDonations().get(j).getMoney());
            }
            indexRowUniversity += 2;
        }
    }
}
