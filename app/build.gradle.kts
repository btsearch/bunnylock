import com.android.apksig.ApkSigner
import com.android.apksig.SigningCertificateLineage
import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.artifact.SingleArtifact
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val releaseSigning = providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties"))
    .asText.orNull
    ?.let { Properties().apply { load(it.reader()) } }

android {
    namespace = "dev.ririxi.bunnylock"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.ririxi.bunnylock"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.3.4"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    signingConfigs {
        if (releaseSigning !== null) {
            create("release") {
                storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        localeFilters += listOf("en", "pl", "zh-rCN")
    }

    packaging {
        // The root helper System.load() libbunnyqrtr.so from nativeLibraryDir, so it must be extracted.
        jniLibs.useLegacyPackaging = true
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val signing = releaseSigning ?: return@onVariants
        val lineageFile = signing.getProperty("lineageFile") ?: return@onVariants
        val signTask = tasks.register<SignWithLineageTask>("sign${variant.name.replaceFirstChar(Char::uppercaseChar)}ApkWithLineage") {
            keystore.set(rootProject.file(signing.getProperty("storeFile")))
            storePassword.set(signing.getProperty("storePassword"))
            keyAlias.set(signing.getProperty("keyAlias"))
            keyPassword.set(signing.getProperty("keyPassword"))
            lineage.set(rootProject.file(lineageFile))
        }
        val request = variant.artifacts.use(signTask)
            .wiredWithDirectories(SignWithLineageTask::apks, SignWithLineageTask::signedApks)
            .toTransformMany(SingleArtifact.APK)
        signTask.configure { transformationRequest.set(request) }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(project(":modem"))
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.hidden.api.bypass)
    debugImplementation(libs.compose.ui.tooling)
}

abstract class SignWithLineageTask : DefaultTask() {
    @get:InputFiles
    abstract val apks: DirectoryProperty

    @get:OutputDirectory
    abstract val signedApks: DirectoryProperty

    @get:InputFile
    abstract val keystore: RegularFileProperty

    @get:Internal
    abstract val storePassword: Property<String>

    @get:Input
    abstract val keyAlias: Property<String>

    @get:Internal
    abstract val keyPassword: Property<String>

    @get:InputFile
    abstract val lineage: RegularFileProperty

    @get:Internal
    abstract val transformationRequest: Property<ArtifactTransformationRequest<SignWithLineageTask>>

    @TaskAction
    fun sign() {
        val store = KeyStore.getInstance(keystore.get().asFile, storePassword.get().toCharArray())
        val key = store.getKey(keyAlias.get(), keyPassword.get().toCharArray()) as PrivateKey
        val certificates = store.getCertificateChain(keyAlias.get()).map { it as X509Certificate }
        val signer = ApkSigner.SignerConfig.Builder(keyAlias.get(), key, certificates).build()
        val signingLineage = SigningCertificateLineage.readFromFile(lineage.get().asFile)
        transformationRequest.get().submit(this) { apk ->
            val input = File(apk.outputFile)
            val output = signedApks.file(input.name).get().asFile
            ApkSigner.Builder(listOf(signer))
                .setInputApk(input)
                .setOutputApk(output)
                .setSigningCertificateLineage(signingLineage)
                .setMinSdkVersionForRotation(28)
                .setV1SigningEnabled(false)
                .setV2SigningEnabled(false)
                .build()
                .sign()
            output
        }
    }
}
