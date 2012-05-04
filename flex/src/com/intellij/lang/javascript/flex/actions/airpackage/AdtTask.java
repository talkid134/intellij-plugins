package com.intellij.lang.javascript.flex.actions.airpackage;

import com.intellij.lang.javascript.flex.actions.ExternalTask;
import com.intellij.lang.javascript.flex.build.FlexCompilationUtils;
import com.intellij.lang.javascript.flex.projectStructure.model.AirPackagingOptions;
import com.intellij.lang.javascript.flex.projectStructure.model.AirSigningOptions;
import com.intellij.lang.javascript.flex.projectStructure.model.FlexIdeBuildConfiguration;
import com.intellij.lang.javascript.flex.projectStructure.model.IosPackagingOptions;
import com.intellij.lang.javascript.flex.run.FlexBaseRunner;
import com.intellij.lang.javascript.flex.sdk.FlexSdkUtils;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import gnu.trove.THashSet;

import java.util.List;
import java.util.Set;

import static com.intellij.lang.javascript.flex.projectStructure.model.AirPackagingOptions.FilePathAndPathInPackage;

public abstract class AdtTask extends ExternalTask {

  public AdtTask(Project project, Sdk flexSdk) {
    super(project, flexSdk);
  }

  @Override
  protected List<String> createCommandLine() {
    final List<String> command = FlexSdkUtils.getCommandLineForSdkTool(myProject, myFlexSdk, null, "com.adobe.air.ADT", "adt.jar");
    appendAdtOptions(command);
    return command;
  }

  protected abstract void appendAdtOptions(final List<String> command);

  public static void appendSigningOptions(final List<String> command,
                                          final AirPackagingOptions packagingOptions,
                                          final String keystorePassword,
                                          final String keyPassword) {
    final AirSigningOptions signingOptions = packagingOptions.getSigningOptions();
    final boolean tempCertificate = !(packagingOptions instanceof IosPackagingOptions) && signingOptions.isUseTempCertificate();

    if (!tempCertificate && !signingOptions.getKeyAlias().isEmpty()) {
      command.add("-alias");
      command.add(signingOptions.getKeyAlias());
    }

    command.add("-storetype");
    command.add(tempCertificate ? AirPackageUtil.PKCS12_KEYSTORE_TYPE : signingOptions.getKeystoreType());

    command.add("-keystore");
    command.add(FileUtil.toSystemDependentName(tempCertificate ? AirPackageUtil.getTempKeystorePath() : signingOptions.getKeystorePath()));

    command.add("-storepass");
    command.add(tempCertificate ? AirPackageUtil.TEMP_KEYSTORE_PASSWORD : keystorePassword);

    if (!tempCertificate) {
      if (!signingOptions.getKeyAlias().isEmpty() && !keyPassword.isEmpty()) {
        command.add("-keypass");
        command.add(keyPassword);
      }

      if (!signingOptions.getProvider().isEmpty()) {
        command.add("-providerName");
        command.add(signingOptions.getProvider());
      }

      if (!signingOptions.getTsa().isEmpty()) {
        command.add("-tsa");
        command.add(signingOptions.getTsa());
      }
    }
  }

  public static void appendPaths(final List<String> command,
                                 final Module module,
                                 final FlexIdeBuildConfiguration bc,
                                 final AirPackagingOptions packagingOptions,
                                 final String packageFileExtension) {
    final String outputFilePath = bc.getActualOutputFilePath();
    final String outputFolder = PathUtil.getParentPath(outputFilePath);

    command.add(FileUtil.toSystemDependentName(outputFolder + "/" + packagingOptions.getPackageFileName() + packageFileExtension));
    command.add(FileUtil.toSystemDependentName(FlexBaseRunner.getAirDescriptorPath(bc, packagingOptions)));

    appendANEPaths(command, module, bc);

    command.add("-C");
    command.add(FileUtil.toSystemDependentName(PathUtil.getParentPath(outputFilePath)));
    command.add(FileUtil.toSystemDependentName(PathUtil.getFileName(outputFilePath)));

    for (FilePathAndPathInPackage entry : packagingOptions.getFilesToPackage()) {
      final String fullPath = FileUtil.toSystemIndependentName(entry.FILE_PATH.trim());
      String relPathInPackage = FileUtil.toSystemIndependentName(entry.PATH_IN_PACKAGE.trim());
      if (relPathInPackage.startsWith("/")) {
        relPathInPackage = relPathInPackage.substring(1);
      }

      final String pathEnd = "/" + relPathInPackage;
      if (fullPath.endsWith(pathEnd)) {
        command.add("-C");
        command.add(FileUtil.toSystemDependentName(fullPath.substring(0, fullPath.length() - pathEnd.length())));
        command.add(FileUtil.toSystemDependentName(relPathInPackage));
      }
      else {
        command.add("-e");
        command.add(FileUtil.toSystemDependentName(fullPath));
        command.add(relPathInPackage);
      }
    }
  }

  private static void appendANEPaths(final List<String> command, final Module module, final FlexIdeBuildConfiguration bc) {
    final Set<VirtualFile> extDirPaths = new THashSet<VirtualFile>();
    for (VirtualFile aneFile : FlexCompilationUtils.getANEFiles(module, bc.getDependencies())) {
      if (extDirPaths.add(aneFile.getParent())) {
        command.add("-extdir");
        command.add(FileUtil.toSystemDependentName(aneFile.getParent().getPath()));
      }
    }
  }
}
