# ScriptBlockNext

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-purple.svg)](https://kotlinlang.org)
[![Paper](https://img.shields.io/badge/API-Paper%201.13.2-black.svg)](https://papermc.io/)

**ScriptBlockNext** は、従来の ScriptBlockPlus の主要機能を再現しつつ、さらなる軽量化を実現した Minecraft サーバー用プラグインです。

既存のスクリプト環境からのシームレスな移行をサポートし、最新の主要プラグインとの連携機能を新たに備えています。

---

## ✨ 主な特徴

- 🚀 **軽量化・最適化**: ScriptBlockPlus のコア機能を見直し、パフォーマンスを向上させています。
- 🔄 **簡単な移行機能**: ScriptBlockPlus からの移行機能を搭載。簡単な設定を行うだけで既存のスクリプトを移行できます。
- 🔌 **モダンな連携プラグイン**: MythicMobs など、新たなエコシステムを拡張するためのプラグイン連携を標準サポートしています。

## 📦 連携プラグイン

ScriptBlockNext は、以下のプラグインとの連携をサポートしています。

- **Vault** (経済・権限システム)
- **PlaceholderAPI** (プレースホルダー拡張)
- **MythicMobs** (カスタムモブ・スキル)
- **LuckPerms** (高度な権限管理)
- **DiscordSRV** (Discord 連携)

## ⚠️ 備考・注意事項

- 🚧 **開発ステータス**: 現在も開発中のため、予期せぬバグが発生する可能性があります。
- ℹ️ **互換性**: ScriptBlockPlus に存在するすべてのコマンドが実装されているわけではありません。

## 📄 ライセンス

このプロジェクトは [GNU General Public License v3.0](LICENSE) のもとで公開されています。

## 🛠 コマンド登録の書き方

`[ ]` で囲んだ単位が1行のスクリプトになります。`[` `]` はネスト可能で、対にならない場合は `\[` `\]` と書けます。

```
/sbn interact create [@cooldown:10] [@if %player_level% >= 5 レベル不足(%value1%)] [@command spawn] [@msg &aようこそ]
```

`&`, `<`, `>` などの記号はそのまま入力できます(JSON は UTF-8 / HTML エスケープなしで保存)。従来の `\b` `\c` `\o` `\s` などのエスケープも利用可能です。
