# An IntelliJ plugin for [Bazel](http://bazel.build) projects

This is an early-access version of our Bazel plugins for IntelliJ,
Android Studio, and CLion.

This code drop is for educational purposes only and is currently
not kept up-to-date. It may not build correctly for you, and
we are currently not accepting pull requests.

In the near future the repository will be open for business
with live, working code and we will start accepting contributions.

## Installation

You can find our plugin in the Jetbrains plugin repository by going to
`Settings -> Browse Repositories`, and searching for `Bazel`.

## Usage

To import an existing Bazel project, choose `Import Bazel Project`,
and follow the instructions in the project import wizard.

Detailed docs are available [here](http://ij.bazel.build).

## Building the plugin

Install Bazel, then run `bazel build //ijwb:ijwb_bazel_zip --define=ij_product=intellij-latest`
from the project root. This will create a plugin zip in
`bazel-bin/ijwb/ijwb_bazel.zip`.
