# FHIR CSV Transformer

This project is a fork of **[FHIRExtinguisher](https://github.com/JohannesOehm/FhirExtinguisher)** and builds on its existing FHIR transformation functionality.

It removes the original frontend features and focuses on backend-oriented data transformation workflows. In addition to reusing FHIRExtinguisher’s transformation capabilities, this fork introduces a CSV-based round-trip workflow:

1. Transform FHIR data into CSV.
2. Adjust or enrich the CSV data.
3. Transform the modified CSV back into FHIR.

The project is mainly designed to be integrated into **[Cinnamon](https://github.com/KI-AIM/Cinnamon)**, where it can support FHIR workflows.

## Usage

A simple round-trip usage of the package with both FHIR to CSV and CSV to FHIR could look like this:

```kotlin
val fhirContext = FhirContext.forR4()
val parser = fhirContext.newJsonParser()
val bundleTransformer = BundleTransformer(fhirContext)

val extractor = ResourceExtractor.Companion.forR4()
try {
    val content = Files.readString(
        Paths.get("example-bundle.json"),
        StandardCharsets.UTF_8
    )
    val attributes: List<Column>? =
        extractor.getResourceFieldsForEntriesInBundle(content)

    val transformationParameters = TransformationParameters(
        CSVFormat.EXCEL,
        Int.MAX_VALUE,
        attributes,
        addRaw = false,
        addResourceNameToColumn = true,
        useExtendedWideFormatColumnHeaders = true,
    )

    val types = bundleTransformer.getResourceTypesInBundle(content)

    val bundle = parser.parseResource(content)

    val table = bundleTransformer.processBundle(
        content, transformationParameters
    )

    val csvString = table.toString(transformationParameters.csvFormat)

    val reBundle = CsvToFhirTransformer().parseCsvToBundle(table.toString())
```

## Credits

Based on **FHIRExtinguisher** by Johannes Oehm and contributors.

Changes made by Yannik Warnecke | yannik.warnecke@uni-muenster.de

## License

Apache License 2.0