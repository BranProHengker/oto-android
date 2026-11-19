# Making modules for Oto

The module guide moved to the wiki: **[github.com/BranProHengker/oto-android/wiki](https://github.com/BranProHengker/oto-android/wiki/#modules-types)**.

It covers everything that used to be here, and more:

- [What a module is and how it works](https://github.com/BranProHengker/oto-android/wiki/#modules-types), and [your first one in two minutes](https://github.com/BranProHengker/oto-android/wiki/#modules-first)
- [Publishing on GitHub](https://github.com/BranProHengker/oto-android/wiki/#modules-github): attach a zip with `otoymodule` in its name to a release, and people add it by pasting your project's link
- [File structure](https://github.com/BranProHengker/oto-android/wiki/#modules-files) for every kind of module, and [updates](https://github.com/BranProHengker/oto-android/wiki/#modules-install): the update button that shows up when you publish a new release
- [Your own player](https://github.com/BranProHengker/oto-android/wiki/#modules-player): blocks, frosted panels, svg paths, blur, backgrounds, [keyframes](https://github.com/BranProHengker/oto-android/wiki/#modules-player-keyframes), [states and events](https://github.com/BranProHengker/oto-android/wiki/#modules-player-states), a visualizer, style sheets, a customise sheet, buttons, the chevron menu and shader code
- [Your own bar](https://github.com/BranProHengker/oto-android/wiki/#modules-glass) and [your own pages](https://github.com/BranProHengker/oto-android/wiki/#modules-pages) in AGSL, with Liquid Glass as the worked example
- Looks and fonts
- [Song sources](https://github.com/BranProHengker/oto-android/wiki/#modules-web), [lyrics providers](https://github.com/BranProHengker/oto-android/wiki/#modules-lyrics), [song info providers](https://github.com/BranProHengker/oto-android/wiki/#modules-metadata), zip modules and JS Modules scripts
- Sharing, testing, troubleshooting, and the [notes for Oto developers](https://github.com/BranProHengker/oto-android/wiki/#devs)

The wiki's source is [`docs/wiki/index.html`](../docs/wiki/index.html). Change it in the same commit as anything that changes what a module can do.

The catalog people see under **Add a module** is [`catalog.json`](catalog.json) in this folder.
