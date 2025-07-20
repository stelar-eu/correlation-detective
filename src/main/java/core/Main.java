package core;

import _aux.lib;
import algorithms.Algorithm;
import algorithms.baselines.SimpleBaseline;
import algorithms.baselines.SmartBaseline;
import algorithms.performance.CorrelationDetective;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import data_io.DataHandler;
import data_io.FileHandler;
import queries.ResultSet;
import similarities.SimEnum;

import javax.xml.crypto.Data;
import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.logging.Logger;

public class Main {
    public static void main(String[] args) {
//        Run default query
        if (args.length == 0){
            args = new String[]{
                    "/home/jens/ownCloud/Documents/3.Werk/0.TUe_Research/1.SimilarityDetective/X.GitHub/library/src/test/resources/input_dev.json",
                    "/home/jens/ownCloud/Documents/3.Werk/0.TUe_Research/1.SimilarityDetective/X.GitHub/library/src/test/resources/output.json",
            };
        }

        try{

        //        Get input and output json path
                String inputJsonPath = args[0];
                String outputJsonPath = args[1];

        //        Read parameters from args
                RunParameters runParameters = parseArgs(inputJsonPath);

        //        Run the query
                run(runParameters);

        //        Get output json
                JsonObject response = getResponse(runParameters);

        //        Write the output json to the output path
                FileHandler fileHandler = new FileHandler();
                fileHandler.writeToFile(outputJsonPath, response.toString());

        } catch (Exception e){

                JsonObject response = new JsonObject();
                response.addProperty("message", "An error occurred while running the query: " + e.getMessage());
                response.addProperty("status", 500);
                
                FileHandler fileHandler = new FileHandler();
                fileHandler.writeToFile(args[1], response.toString());
        }

        System.exit(0);
    }

    public static RunParameters parseArgs(String inputJsonPath){
//        Read input json as Json object
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            JsonNode jsonNode = objectMapper.readTree(new File(inputJsonPath));

            // First parse the required arguments
            String inputPath = jsonNode.get("input").get("data_file").get(0).asText();

            String outputPath = jsonNode.get("output").get("correlations_file").asText();
            JsonNode parameters = jsonNode.get("parameters");
            SimEnum simMetricName = SimEnum.valueOf(parameters.get("simMetricName").asText().toUpperCase());
            int maxPLeft = parameters.get("maxPLeft").asInt();
            int maxPRight = parameters.get("maxPRight").asInt();

//            Create a new RunParameters object
            RunParameters runParameters = new RunParameters(inputPath, simMetricName, maxPLeft, maxPRight);
            runParameters.setOutputPath(outputPath);

//            Set the optional parameters
            Iterator<String> fieldNames = parameters.fieldNames();
            while (fieldNames.hasNext()){
                String fieldName = fieldNames.next();
                if (!fieldName.equals("simMetricName") && !fieldName.equals("maxPLeft") && !fieldName.equals("maxPRight")){
                    runParameters.set(fieldName, parameters.get(fieldName).asText());
                }
            }

//            Set the minio environment variables if given
            if (jsonNode.has("minio")){
                JsonNode minio = jsonNode.get("minio");
                System.setProperty("MINIO_ENDPOINT_URL", minio.get("endpoint_url").asText());
                System.setProperty("MINIO_ACCESS_KEY", minio.get("id").asText());
                System.setProperty("MINIO_SECRET_KEY", minio.get("key").asText());

                if (minio.has("skey")){
                    System.setProperty("MINIO_SESSION_TOKEN", minio.get("skey").asText());
                }
            }

            return runParameters;
        } catch (Exception e){
            e.printStackTrace();
        }

        return null;
    }

    private static void run(RunParameters runParameters) {
        Algorithm algorithm;
        switch (runParameters.getAlgorithm()){
            case SIMILARITY_DETECTIVE: default: algorithm = new CorrelationDetective(runParameters); break;
            case SIMPLE_BASELINE: algorithm = new SimpleBaseline(runParameters); break;
            case SMART_BASELINE: algorithm = new SmartBaseline(runParameters); break;
        }

//        Run the algorithm
        algorithm.run();

//        Save the results and stats
        saveResults(runParameters, runParameters.getOutputPath());
    }

    private static void saveResults(RunParameters runParameters, String outputPath){
//        Add a random run identifier to the outputPath
//        outputPath += "/" + UUID.randomUUID().toString();
        Logger.getGlobal().info("Saving results and stats to " + outputPath);

        //        Create the output directory
        lib.createDir(outputPath);

        DataHandler outputHandler = runParameters.getOutputHandler();
        StatBag statBag = runParameters.getStatBag();
        ResultSet resultSet = runParameters.getResultSet();

//        Save the results as a json file
        outputHandler.writeToFile(outputPath, resultSet.toJson());
    }

    private static JsonObject getResponse(RunParameters runParameters){
        //        Prepare the output response
        JsonObject outputJson = new JsonObject();
        outputJson.addProperty("message", "Correlation Detective run completed successfully");


        JsonObject outputObject = new JsonObject();
        outputObject.addProperty("correlations_file", "s3://"+runParameters.getOutputPath());
        
        outputJson.add("output", outputObject);

//        Parameters
        JsonObject metrics = new JsonObject();
        metrics.add("statistics", runParameters.getStatBag().toJsonElement().getAsJsonObject());

        outputJson.add("metrics", metrics);

        outputJson.addProperty("status", 200);

        return outputJson;
    }
}