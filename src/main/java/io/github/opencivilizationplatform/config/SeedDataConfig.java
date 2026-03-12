package io.github.opencivilizationplatform.config;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import io.github.opencivilizationplatform.modules.monitoring.domain.BiosphereMetric;
import io.github.opencivilizationplatform.modules.monitoring.infrastructure.BiosphereMetricRepository;
import io.github.opencivilizationplatform.modules.resources.domain.Resource;
import io.github.opencivilizationplatform.modules.resources.infrastructure.ResourceRepository;
import io.github.opencivilizationplatform.modules.needs.domain.Need;
import io.github.opencivilizationplatform.modules.needs.infrastructure.NeedRepository;
import io.github.opencivilizationplatform.modules.production.domain.Facility;
import io.github.opencivilizationplatform.modules.production.infrastructure.FacilityRepository;
import io.github.opencivilizationplatform.modules.logistics.domain.Shipment;
import io.github.opencivilizationplatform.modules.logistics.infrastructure.ShipmentRepository;
import io.github.opencivilizationplatform.modules.execution.domain.AutomationUnit;
import io.github.opencivilizationplatform.modules.execution.infrastructure.AutomationUnitRepository;
import io.github.opencivilizationplatform.modules.participation.domain.Interaction;
import io.github.opencivilizationplatform.modules.participation.infrastructure.InteractionRepository;
import io.github.opencivilizationplatform.modules.participation.domain.Rule;
import io.github.opencivilizationplatform.modules.participation.infrastructure.RuleRepository;
import io.github.opencivilizationplatform.modules.governance.domain.ScientificCommittee;
import io.github.opencivilizationplatform.modules.governance.infrastructure.ScientificCommitteeRepository;
import io.github.opencivilizationplatform.modules.social.domain.BehaviorAssessment;
import io.github.opencivilizationplatform.modules.social.domain.Case;
import io.github.opencivilizationplatform.modules.social.domain.Incident;
import io.github.opencivilizationplatform.modules.social.infrastructure.BehaviorAssessmentRepository;
import io.github.opencivilizationplatform.modules.social.infrastructure.CaseRepository;
import io.github.opencivilizationplatform.modules.social.infrastructure.IncidentRepository;
import io.github.opencivilizationplatform.modules.contribution.domain.Citizen;
import io.github.opencivilizationplatform.modules.contribution.domain.Contribution;
import io.github.opencivilizationplatform.modules.contribution.domain.Project;
import io.github.opencivilizationplatform.modules.contribution.domain.Skill;
import io.github.opencivilizationplatform.modules.contribution.infrastructure.CitizenRepository;
import io.github.opencivilizationplatform.modules.contribution.infrastructure.ContributionRepository;
import io.github.opencivilizationplatform.modules.contribution.infrastructure.ProjectRepository;
import io.github.opencivilizationplatform.modules.contribution.infrastructure.SkillRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.List;

@Configuration
public class SeedDataConfig {

    private final ResourceRepository resourceRepository;
    private final NeedRepository needRepository;
    private final FacilityRepository facilityRepository;
    private final ShipmentRepository shipmentRepository;
    private final InteractionRepository interactionRepository;
    private final BiosphereMetricRepository biosphereMetricRepository;
    private final RuleRepository ruleRepository;
    private final AutomationUnitRepository automationUnitRepository;
    private final ScientificCommitteeRepository committeeRepository;
    private final IncidentRepository incidentRepository;
    private final BehaviorAssessmentRepository assessmentRepository;
    private final CaseRepository caseRepository;
    private final CitizenRepository citizenRepository;
    private final SkillRepository skillRepository;
    private final ProjectRepository projectRepository;

    public SeedDataConfig(ResourceRepository resourceRepository,
                          NeedRepository needRepository,
                          FacilityRepository facilityRepository,
                          ShipmentRepository shipmentRepository,
                          InteractionRepository interactionRepository,
                          BiosphereMetricRepository biosphereMetricRepository,
                          RuleRepository ruleRepository,
                          AutomationUnitRepository automationUnitRepository,
                          ScientificCommitteeRepository committeeRepository,
                          IncidentRepository incidentRepository,
                          BehaviorAssessmentRepository assessmentRepository,
                          CaseRepository caseRepository,
                          CitizenRepository citizenRepository,
                          SkillRepository skillRepository,
                          ProjectRepository projectRepository) {
        this.resourceRepository = resourceRepository;
        this.needRepository = needRepository;
        this.facilityRepository = facilityRepository;
        this.shipmentRepository = shipmentRepository;
        this.interactionRepository = interactionRepository;
        this.biosphereMetricRepository = biosphereMetricRepository;
        this.ruleRepository = ruleRepository;
        this.automationUnitRepository = automationUnitRepository;
        this.committeeRepository = committeeRepository;
        this.incidentRepository = incidentRepository;
        this.assessmentRepository = assessmentRepository;
        this.caseRepository = caseRepository;
        this.citizenRepository = citizenRepository;
        this.skillRepository = skillRepository;
        this.projectRepository = projectRepository;
    }

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @Bean
    public CommandLineRunner initData() {
        return args -> {
            if (resourceRepository.count() == 0) {
                Resource carajas = new Resource();
                carajas.setName("Carajás Iron Mine");
                carajas.setType("MINERAL");
                carajas.setDescription("Largest iron ore mine in the world, located in Pará, Brazil.");
                carajas.setLocation(createPoint(-6.06, -50.15));
                carajas.setQuantity(18.0);
                carajas.setUnit("Billion Tons");

                Resource atacama = new Resource();
                atacama.setName("Atacama Lithium Deposit");
                atacama.setType("MINERAL");
                atacama.setDescription("Major lithium brine deposit in Chile, crucial for energy storage.");
                atacama.setLocation(createPoint(-23.5, -68.25));
                atacama.setQuantity(9.0);
                atacama.setUnit("Million Tons");

                Resource saharaSolar = new Resource();
                saharaSolar.setName("Sahara Solar Potential Zone");
                saharaSolar.setType("ENERGY");
                saharaSolar.setDescription("High-intensity solar radiation zone with massive energy capacity.");
                saharaSolar.setLocation(createPoint(23.5, 12.0));
                saharaSolar.setQuantity(1000.0);
                saharaSolar.setUnit("GWp");

                Resource northSeaWind = new Resource();
                northSeaWind.setName("North Sea Wind Hub");
                northSeaWind.setType("ENERGY");
                northSeaWind.setDescription("Critical offshore wind potential for Northern Europe.");
                northSeaWind.setLocation(createPoint(55.0, 3.0));
                northSeaWind.setQuantity(50.0);
                northSeaWind.setUnit("GW");

                Resource wheatBelt = new Resource();
                wheatBelt.setName("Global Wheat Belt");
                wheatBelt.setType("FOOD");
                wheatBelt.setDescription("High-yield grain production zone.");
                wheatBelt.setLocation(createPoint(45.0, -100.0));
                wheatBelt.setQuantity(800.0);
                wheatBelt.setUnit("Million Tons");

                Resource housingStock = new Resource();
                housingStock.setName("Initial Sustainable Housing Stock");
                housingStock.setType("HOUSING");
                housingStock.setDescription("Existing baseline of RBE-compliant housing.");
                housingStock.setLocation(createPoint(0.0, 0.0));
                housingStock.setQuantity(5.0);
                housingStock.setUnit("Million Units");

                resourceRepository.saveAll(List.of(carajas, atacama, saharaSolar, northSeaWind, wheatBelt, housingStock));
                System.out.println(">> Seeded initial resource data successfully.");

                seedNeeds();
                seedFacilities();
                seedShipments();
                seedInteractions();
                seedBiosphereMetrics();
                seedRules();
                seedAutomationUnits();
                seedCommittees();
                seedSocialStability();
                seedContribution();
            }
        };
    }

    private void seedBiosphereMetrics() {
        BiosphereMetric co2 = new BiosphereMetric();
        co2.setName("Atmospheric CO2 Concentration");
        co2.setValue(419.5);
        co2.setUnit("ppm");
        co2.setSafetyLimit(350.0);
        co2.setStatus("CRITICAL");
        co2.setDrift(2.4);

        BiosphereMetric temp = new BiosphereMetric();
        temp.setName("Global Surface Temp Deviation");
        temp.setValue(1.15);
        temp.setUnit("°C");
        temp.setSafetyLimit(1.5);
        temp.setStatus("WARNING");
        temp.setDrift(0.02);

        BiosphereMetric oceanPh = new BiosphereMetric();
        oceanPh.setName("Ocean Surface Acidity");
        oceanPh.setValue(8.06);
        oceanPh.setUnit("pH");
        oceanPh.setSafetyLimit(8.1);
        oceanPh.setStatus("WARNING");
        oceanPh.setDrift(-0.002);

        BiosphereMetric forest = new BiosphereMetric();
        forest.setName("Global Reforestation Rate");
        forest.setValue(4.2);
        forest.setUnit("Million Hectares/Year");
        forest.setSafetyLimit(10.0);
        forest.setStatus("NORMAL");
        forest.setDrift(0.5);

        biosphereMetricRepository.saveAll(List.of(co2, temp, oceanPh, forest));
        System.out.println(">> Seeded initial biosphere health metrics successfully.");
    }

    private void seedInteractions() {
        Interaction waterReport = new Interaction();
        waterReport.setType("NEED_REPORT");
        waterReport.setContent("Local aquifer levels dropping significantly in the Central Plain region. Requesting hydro-desalination assessment.");
        waterReport.setRegion("Central Plains");
        waterReport.setCitizenId("CIT-9928");
        waterReport.setStatus("VERIFIED");

        Interaction modularDesign = new Interaction();
        modularDesign.setType("INNOVATION");
        modularDesign.setContent("Proposed upgrade to 3D-Housing extrusion head for 15% faster curing using carbon-fiber composite.");
        modularDesign.setRegion("Global");
        modularDesign.setCitizenId("CIT-4412");
        modularDesign.setStatus("INTEGRATED");

        Interaction energyCollab = new Interaction();
        energyCollab.setType("COLLABORATION");
        energyCollab.setContent("Registered for experimental thorium reactor maintenance simulation in Northern Europe sector.");
        energyCollab.setRegion("EU-North");
        energyCollab.setCitizenId("CIT-1055");
        energyCollab.setStatus("PENDING");

        interactionRepository.saveAll(List.of(waterReport, modularDesign, energyCollab));
        System.out.println(">> Seeded initial interaction data successfully.");
    }

    private void seedShipments() {
        Shipment lithiumTransit = new Shipment();
        lithiumTransit.setCargo("Lithium Carbonate");
        lithiumTransit.setOrigin("Atacama Desert, Chile");
        lithiumTransit.setDestination("Global Battery Hub");
        lithiumTransit.setQuantity(500.0);
        lithiumTransit.setUnit("Tons");
        lithiumTransit.setStatus("IN_TRANSIT");
        lithiumTransit.setEta(LocalDateTime.now().plusDays(5));

        Shipment foodRelief = new Shipment();
        foodRelief.setCargo("Bio-Nutritional Matrix");
        foodRelief.setOrigin("Agro-Synthesis Alpha, SSA");
        foodRelief.setDestination("Regional Distribution Center 04");
        foodRelief.setQuantity(200.0);
        foodRelief.setUnit("Tons");
        foodRelief.setStatus("IN_TRANSIT");
        foodRelief.setEta(LocalDateTime.now().plusDays(2));

        Shipment ironSupply = new Shipment();
        ironSupply.setCargo("Refined Iron Ore");
        ironSupply.setOrigin("Carajás Mine, Brazil");
        ironSupply.setDestination("Automated Construction SEA-01");
        ironSupply.setQuantity(1200.0);
        ironSupply.setUnit("Tons");
        ironSupply.setStatus("PENDING");
        ironSupply.setEta(LocalDateTime.now().plusDays(10));

        shipmentRepository.saveAll(List.of(lithiumTransit, foodRelief, ironSupply));
        System.out.println(">> Seeded initial shipment data successfully.");
    }

    private void seedFacilities() {
        Facility buildingHub = new Facility();
        buildingHub.setName("Neo-Architectural Hub SEA-01");
        buildingHub.setType("HOUSING_3D");
        buildingHub.setRegion("Southeast Asia");
        buildingHub.setEfficiency(0.92);
        buildingHub.setStatus("ACTIVE");
        buildingHub.setCurrentOutput("450 units/month");

        Facility verticalFarm = new Facility();
        verticalFarm.setName("Agro-Synthesis Alpha");
        verticalFarm.setType("VERTICAL_FARM");
        verticalFarm.setRegion("Sub-Saharan Africa");
        verticalFarm.setEfficiency(0.88);
        verticalFarm.setStatus("ACTIVE");
        verticalFarm.setCurrentOutput("15,000 kg/day");

        Facility recyclingHub = new Facility();
        recyclingHub.setName("Molecular Re-Integrator 01");
        recyclingHub.setType("RECYCLING_HUB");
        recyclingHub.setRegion("Global");
        recyclingHub.setEfficiency(0.95);
        recyclingHub.setStatus("ACTIVE");
        recyclingHub.setCurrentOutput("1.2 tons/hour");

        facilityRepository.saveAll(List.of(buildingHub, verticalFarm, recyclingHub));
        System.out.println(">> Seeded initial automated facilities data successfully.");
    }

    private void seedNeeds() {
        Need housingSEA = new Need();
        housingSEA.setCategory("HOUSING");
        housingSEA.setRegion("Southeast Asia");
        housingSEA.setDescription("Unmet demand for sustainable, high-density housing units.");
        housingSEA.setQuantity(15.0);
        housingSEA.setUnit("Million Units");
        housingSEA.setPriority(5);
        housingSEA.setStatus("UNMET");

        Need nutritionSSA = new Need();
        nutritionSSA.setCategory("FOOD");
        nutritionSSA.setRegion("Sub-Saharan Africa");
        nutritionSSA.setDescription("Daily caloric target deficit for child population.");
        nutritionSSA.setQuantity(2.5);
        nutritionSSA.setUnit("Billion kcal/day");
        nutritionSSA.setPriority(5);
        nutritionSSA.setStatus("PARTIAL");

        Need energyEU = new Need();
        energyEU.setCategory("ENERGY");
        energyEU.setRegion("European Union");
        energyEU.setDescription("Target for 100% renewable energy transition.");
        energyEU.setQuantity(300.0);
        energyEU.setUnit("TWh/year");
        energyEU.setPriority(4);
        energyEU.setStatus("PARTIAL");

        Need educationGlobal = new Need();
        educationGlobal.setCategory("EDUCATION");
        educationGlobal.setRegion("Global");
        educationGlobal.setDescription("Open access to advanced scientific and technical training.");
        educationGlobal.setQuantity(1.2);
        educationGlobal.setUnit("Billion People");
        educationGlobal.setPriority(4);
        educationGlobal.setStatus("UNMET");

        Need mineralNeed = new Need();
        mineralNeed.setCategory("MINERAL");
        mineralNeed.setRegion("Global");
        mineralNeed.setDescription("Resource requirement for global structural transition.");
        mineralNeed.setQuantity(5.0);
        mineralNeed.setUnit("Billion Tons");
        mineralNeed.setPriority(3);
        mineralNeed.setStatus("PARTIAL");

        needRepository.saveAll(List.of(housingSEA, nutritionSSA, energyEU, educationGlobal, mineralNeed));
        System.out.println(">> Seeded initial human needs data successfully.");
    }

    private void seedRules() {
        Rule biospherePriority = new Rule();
        biospherePriority.setTitle("Biosphere Stability Clause");
        biospherePriority.setDescription("All industrial production must cease in a region if local biodiversity indices drop below therapeutic thresholds.");
        biospherePriority.setLogicCode("{\"type\": \"THRESHOLD_TRIGGER\", \"metric\": \"BIOSPHERE_HEALTH\", \"action\": \"SUSPEND_PRODUCTION\"}");
        biospherePriority.setStatus("ACTIVE");
        biospherePriority.setValidationStatus("SCIENTIFICALLY_VALIDATED");
        biospherePriority.setValidatedBy("Global Biosphere Commission");
        biospherePriority.setVotesCount(12500);

        Rule caloricSecurity = new Rule();
        caloricSecurity.setTitle("Universal Caloric Security");
        caloricSecurity.setDescription("Strategic reserves must maintain a 6-month buffer of essential nutrients before export is authorized.");
        caloricSecurity.setLogicCode("{\"type\": \"RESERVE_CHECK\", \"metric\": \"FOOD\", \"min_buffer_months\": 6}");
        caloricSecurity.setStatus("ACTIVE");
        caloricSecurity.setValidationStatus("SCIENTIFICALLY_VALIDATED");
        caloricSecurity.setValidatedBy("Energy & Nutrition Council");
        caloricSecurity.setVotesCount(8900);

        ruleRepository.saveAll(List.of(biospherePriority, caloricSecurity));
        System.out.println(">> Seeded initial constitutional rules successfully.");
    }

    private void seedAutomationUnits() {
        AutomationUnit constructor01 = new AutomationUnit();
        constructor01.setName("Constructor Alpha-1");
        constructor01.setType("CONSTRUCTOR");
        constructor01.setRegion("Southeast Asia");
        constructor01.setStatus("ACTIVE");
        constructor01.setCurrentTask("ASSEMBLING_MODULAR_HOUSING");

        AutomationUnit droneSwarmBeta = new AutomationUnit();
        droneSwarmBeta.setName("Agro-Drone Swarm Beta");
        droneSwarmBeta.setType("DRONE");
        droneSwarmBeta.setRegion("Sub-Saharan Africa");
        droneSwarmBeta.setStatus("ACTIVE");
        droneSwarmBeta.setCurrentTask("MONITORING_CROP_MATURITY");

        AutomationUnit repairBotGamma = new AutomationUnit();
        repairBotGamma.setName("Maint-Bot Gamma-4");
        repairBotGamma.setType("BOT");
        repairBotGamma.setRegion("European Union");
        repairBotGamma.setStatus("IDLE");
        repairBotGamma.setCurrentTask("STANDBY");

        automationUnitRepository.saveAll(List.of(constructor01, droneSwarmBeta, repairBotGamma));
        System.out.println(">> Seeded initial automation units successfully.");
    }

    private void seedCommittees() {
        ScientificCommittee bioComm = new ScientificCommittee();
        bioComm.setArea("BIOSPHERE");
        bioComm.setName("Global Biosphere Commission");
        bioComm.setMandate("Auditing planetary boundaries and biodiversity indices.");
        bioComm.setValidationLevel("EMPIRICAL_VALIDATED");

        ScientificCommittee energyComm = new ScientificCommittee();
        energyComm.setArea("ENERGY");
        energyComm.setName("Energy & Nutrition Council");
        energyComm.setMandate("Optimizing thermodynamic efficiency in food and power systems.");
        energyComm.setValidationLevel("PEER_REVIEWED");

        committeeRepository.saveAll(List.of(bioComm, energyComm));
        System.out.println(">> Seeded initial scientific committees successfully.");
    }

    private void seedSocialStability() {
        Incident dispute = new Incident();
        dispute.setType("CONFLICT");
        dispute.setLocation("Sector 7 Community Garden");
        dispute.setDescription("Resource allocation dispute regarding irrigation timing.");
        dispute.setRiskLevel("LOW");
        dispute.setStatus("ANALYZING");
        dispute.setParticipantIds(List.of("CIT-8821", "CIT-3310"));

        Incident behavioralAnomaly = new Incident();
        behavioralAnomaly.setType("BEHAVIORAL_ANOMALY");
        behavioralAnomaly.setLocation("Urban Transit Node 04");
        behavioralAnomaly.setDescription("Citizen showing signs of extreme stress and erratic behavior.");
        behavioralAnomaly.setRiskLevel("MEDIUM");
        behavioralAnomaly.setStatus("REPORTED");
        behavioralAnomaly.setParticipantIds(List.of("CIT-1055"));

        incidentRepository.saveAll(List.of(dispute, behavioralAnomaly));

        BehaviorAssessment assessment = new BehaviorAssessment();
        assessment.setCitizenId("CIT-1055");
        assessment.setPsychologicalProfile("High stress levels detected via biometrics. History of displacement trauma.");
        assessment.setRiskScore(0.45);
        assessment.setSocialFactors("Recent relocation to high-density zone; lack of familiar social cues.");

        assessmentRepository.save(assessment);

        Case socialCase = new Case();
        socialCase.setSourceIncident(behavioralAnomaly);
        socialCase.setStatus("REHABILITATION");
        socialCase.setResolutionPlan("Relocation to low-density green zone and assignment of a behavioral mediator.");
        socialCase.setRehabilitationProgram("Cognitive-behavioral support and social integration workshop.");
        socialCase.setMonitoringPlan("Biometric stress monitoring for 3 months.");
        socialCase.setPanelExpertIds(List.of("EXP-PSY-01", "EXP-SOC-04"));

        caseRepository.save(socialCase);
        System.out.println(">> Seeded initial social stability and justice data successfully.");
    }

    private void seedContribution() {
        Skill engineering = new Skill();
        engineering.setName("Engineering");
        engineering.setCategory("ENGINEERING");
        engineering.setDescription("Sustainable systems design.");

        Skill science = new Skill();
        science.setName("Science");
        science.setCategory("SCIENCE");
        science.setDescription("Empirical research and validation.");

        Skill education = new Skill();
        education.setName("Education");
        education.setCategory("EDUCATION");
        education.setDescription("Knowledge transmission.");

        skillRepository.saveAll(List.of(engineering, science, education));

        Citizen jackson = new Citizen();
        jackson.setCitizenId("CIT-0001");
        jackson.setName("Jackson Wendel");
        jackson.setSkills(List.of(engineering, science));
        jackson.setInterests(List.of("Automation", "Sustainability", "DDD"));
        jackson.setReputationScore(150.0);
        jackson.setBiographicalNote("Lead architect of the Civilization Operating System.");

        citizenRepository.save(jackson);

        Project reforestation = new Project();
        reforestation.setTitle("Amazon Restoration Project");
        reforestation.setDescription("Automated reforestation using seed-planting drones and bio-monitoring.");
        reforestation.setCategory("ENVIRONMENTAL");
        reforestation.setImpactArea("REFORESTATION");
        reforestation.setRequiredSkillNames(List.of("Engineering", "Science"));
        reforestation.setStatus("ACTIVE");

        projectRepository.save(reforestation);

        System.out.println(">> Seeded initial contribution and purpose data successfully.");
    }

    private Point createPoint(double lat, double lon) {
        return geometryFactory.createPoint(new Coordinate(lon, lat));
    }
}
