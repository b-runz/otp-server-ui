#!/bin/bash
set -euo pipefail

SOURCES_DIR=/tmp/sources
mkdir -p "$SOURCES_DIR"

echo "Downloading OSM extract..."
curl -fsSL -o "$SOURCES_DIR/denmark-latest.osm.pbf" \
  https://download.geofabrik.de/europe/denmark-latest.osm.pbf

echo "Downloading GTFS feed..."
curl -fsSL -o "$SOURCES_DIR/GTFS.zip" \
  https://www.rejseplanen.info/labs/GTFS.zip

echo "Building graph (unclipped, full Denmark)..."
/app/bin/graph-builder \
  "$SOURCES_DIR/denmark-latest.osm.pbf" \
  "$SOURCES_DIR/GTFS.zip" \
  /output/graph.obj.new
