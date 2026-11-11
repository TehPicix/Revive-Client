# 01. Source Reconstruction

This section contains all the source code I reconstructed from the shipped artifacts of the **Revive** client, loader, and website. The goal is to rebuild the original source into a clean, buildable state for analysis and verification.

The reconstructed source is organized into the following directories:
- [**`revive-client`**](revive-client): the client itself, a Fabric mod forked from Meteor Client.
- [**`revive-launcher`**](revive-launcher): the Electron launcher that signs the user in, installs the loader, and starts the game.
- [**`revive-loader`**](revive-loader): the Fabric `preLaunch` loader that authorizes the user and fetches and injects the client.
- [**`revive-www`**](revive-www): a static archive of the `revive-client.com` website.
- [**`revive-gridfly`**](revive-gridfly): the saved GridFly operator dashboard page (`index.html`), see [`GRIDFLY-PROTOCOL.md`](../02.%20Teardown%20Analysis/GRIDFLY-PROTOCOL.md).

