package de.uni_mannheim.informatik.dws.wdi.SoccerIdentityResolution.matchingRules.dbpedia_euro16;

import de.uni_mannheim.informatik.dws.wdi.SoccerIdentityResolution.ErrorAnalysisPlayers;
import de.uni_mannheim.informatik.dws.wdi.SoccerIdentityResolution.blockers.PlayerBlockerByBirthYear;
import de.uni_mannheim.informatik.dws.wdi.SoccerIdentityResolution.blockers.PlayerBlockerByFirstLettersOfName;
import de.uni_mannheim.informatik.dws.wdi.SoccerIdentityResolution.comparators.*;
import de.uni_mannheim.informatik.dws.wdi.SoccerIdentityResolution.model.Player;
import de.uni_mannheim.informatik.dws.wdi.SoccerIdentityResolution.model.PlayerXMLReader;
import de.uni_mannheim.informatik.dws.winter.matching.MatchingEngine;
import de.uni_mannheim.informatik.dws.winter.matching.MatchingEvaluator;
import de.uni_mannheim.informatik.dws.winter.matching.algorithms.RuleLearner;
import de.uni_mannheim.informatik.dws.winter.matching.blockers.StandardRecordBlocker;
import de.uni_mannheim.informatik.dws.winter.matching.rules.WekaMatchingRule;
import de.uni_mannheim.informatik.dws.winter.model.Correspondence;
import de.uni_mannheim.informatik.dws.winter.model.HashedDataSet;
import de.uni_mannheim.informatik.dws.winter.model.MatchingGoldStandard;
import de.uni_mannheim.informatik.dws.winter.model.Performance;
import de.uni_mannheim.informatik.dws.winter.model.defaultmodel.Attribute;
import de.uni_mannheim.informatik.dws.winter.model.defaultmodel.FeatureVectorDataSet;
import de.uni_mannheim.informatik.dws.winter.model.defaultmodel.RecordCSVFormatter;
import de.uni_mannheim.informatik.dws.winter.model.io.CSVCorrespondenceFormatter;
import de.uni_mannheim.informatik.dws.winter.processing.Processable;

import java.io.File;

/**
 * Data Set Transfermarkt ↔ Kaggle
 * ML for Players
 */
public class IR_weka_players
{

    static boolean WRITE_FEATURE_SET_FOR_EXTERNAL_TOOL = false;

    public static void main( String[] args ) throws Exception
    {

        // loading data
        HashedDataSet<Player, Attribute> datadbpedia = new HashedDataSet<>();
        new PlayerXMLReader().loadFromXML(new File("data/input/dbpedia.xml"), "/clubs/club/players/player", datadbpedia);
        HashedDataSet<Player, Attribute> dataeuro2016 = new HashedDataSet<>();
        new PlayerXMLReader().loadFromXML(new File("data/input/euro2016.xml"), "/clubs/club/players/player", dataeuro2016);

        System.out.println("Sample from dbpedia: " + datadbpedia.getRandomRecord());
        System.out.println("Sample from euro2016: " + dataeuro2016.getRandomRecord());

        String options[] = new String[] {""};
        String modelType = "SimpleLogistic"; // using a logistic regression
        WekaMatchingRule<Player, Attribute> matchingRule = new WekaMatchingRule<>(0.8, modelType, options);

        // add comparators
        matchingRule.addComparator(new PlayerNameComparatorLevenshtein(true));
        matchingRule.addComparator(new PlayerNameComparatorJaroWinkler(true));
        matchingRule.addComparator(new PlayerNameComparatorMongeElkan(true, "doubleMetaphone", true));
        matchingRule.addComparator(new PlayerBirthDateComparatorExactDateComparison());
        matchingRule.addComparator(new PlayerHeightComparator());
        


        // create a blocker (blocking strategy)
        StandardRecordBlocker<Player, Attribute> blocker = new StandardRecordBlocker<Player, Attribute>(new PlayerBlockerByFirstLettersOfName(2));


        // load the gold standard (training set)
        MatchingGoldStandard goldStandardForTraining = new MatchingGoldStandard();
        goldStandardForTraining.loadFromCSVFile(new File("data/goldstandard/completeGoldstandard/gs_dbpedia_2_euro2016_WEKA_test_players.csv"));

        // train the matching rule's model
        RuleLearner<Player, Attribute> learner = new RuleLearner<>();
        learner.learnMatchingRule(datadbpedia, dataeuro2016, null, matchingRule, goldStandardForTraining);

        // Initialize Matching Engine
        MatchingEngine<Player, Attribute> engine = new MatchingEngine<>();

        // Execute the matching
        Processable<Correspondence<Player, Attribute>> correspondences = engine.runIdentityResolution(
                datadbpedia, dataeuro2016, null, matchingRule,
                blocker);

        // write the correspondences to the output file
        new CSVCorrespondenceFormatter().writeCSV(new File("data/output/dbpedia_2_euro2016_correspondences_WEKA_players.csv"), correspondences);


        // gold standard for evaluation
        MatchingGoldStandard goldStandardForEvaluation = new MatchingGoldStandard();
        goldStandardForEvaluation.loadFromCSVFile(new File("data/goldstandard/completeGoldstandard/gs_dbpedia_2_euro2016_WEKA_test_players.csv"));



        // evaluate your result
        MatchingEvaluator<Player, Attribute> evaluator = new MatchingEvaluator<Player, Attribute>(true);
        Performance perfTest = evaluator.evaluateMatching(correspondences.get(),
                goldStandardForEvaluation);
        new ErrorAnalysisPlayers().printFalsePositives(correspondences, goldStandardForEvaluation);
        new ErrorAnalysisPlayers().printFalseNegatives(datadbpedia, dataeuro2016, correspondences, goldStandardForEvaluation);
        // print the evaluation result
        System.out.println("Dbpedia ↔ Kaggle");
        System.out
                .println(String.format(
                        "Precision: %.4f\nRecall: %.4f\nF1: %.4f",
                        perfTest.getPrecision(), perfTest.getRecall(),
                        perfTest.getF1()));


    }

}
