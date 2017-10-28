/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.core.typereg;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.osgi.VersionedName;
import org.apache.brooklyn.util.text.BrooklynVersionSyntax;
import org.apache.brooklyn.util.text.QuotedStringTokenizer;
import org.apache.brooklyn.util.text.Strings;
import org.osgi.framework.Bundle;
import org.osgi.framework.VersionRange;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * Internal class for parsing bundle manifests to extract their upgrade instructions.
 */
public class BundleUpgradeParser {

    /**
     * A header in a bundle's manifest, indicating that this bundle will force the removal of the 
     * given legacy catalog items. Here "legacy" means those in the `/catalog` persisted state, 
     * rather than items added in bundles.
     * 
     * The format for the value is one of the following:
     * <ul>
     *   <li>Quoted {@code name:versionRange}, eg {@code "my-tomcat:[0,1)"};
     *       see {@link #MANIFEST_HEADER_FORCE_REMOVE_BUNDLES} for more information
     *   <li>Comma-separated list of quoted {@code name:versionRange}, eg {@code "my-tomcat:[0,1)","my-nginx:[0,1)"};
     *       see {@link #MANIFEST_HEADER_FORCE_REMOVE_BUNDLES} for more information
     *   <li>{@code "*"} means all legacy items for things defined in this bundle, with version
     *       numbers lower than the version of the bundle. For example, if the bundle is 
     *       version 1.0.0 and its catalog.bom contains items "foo" and "bar", then it is equivalent
     *       to writing {@code "foo:[0,1.0.0)","bar:[0,1.0.0)"}.
     *       As per the comments on {@link #MANIFEST_HEADER_FORCE_REMOVE_BUNDLES},
     *       the version of the bundle and items being added here to replace legacy catalog items
     *       should typically be larger in major/minor/point value 
     *       as a qualifier bump can be quite complex due to ordering differences.  
     * </ul>
     */
    @Beta
    public static final String MANIFEST_HEADER_FORCE_REMOVE_LEGACY_ITEMS = "brooklyn-catalog-force-remove-legacy-items";

    /**
     * A header in a bundle's manifest, indicating that this bundle will force the removal of matching 
     * bundle(s) previously added and the types they contain.
     * 
     * The format for the value is one of:
     * <ul>
     *   <li>Quoted {@code name:versionRange}, where version range follows the OSGi conventions 
     *       (except that a single version number means exactly that version rather than greater 
     *       than or equal to that version). For example, {@code "org.example.mybundle:[0,1)"}.
     *       Note in particular this uses OSGi ordering semantics not Brooklyn ordering semantics,
     *       so qualifiers come <i>after</i> unqualified versions here, snapshot is not special-cased,
     *       and qualifiers (last/fourth segment) are compared alphabetically
     *       (thus "1.0" < "1.0.0.GA" < "1.0.0.SNAPSHOT" < "1.0.0.v10" < "1.0.0.v2" --
     *       but they are the same with respect to major/minor/point numbers so if in doubt stick with those!).
     *       Thus if using a range it is generally recommended to use a "[" square bracket start and ")" round bracket end
     *       so that the start is inclusive and end exclusive, and any edge cases explicitly referenced.
     *       If you want to replace a SNAPSHOT or RC version with a GA version you will need to call this out specially,
     *       as described in the "comma-separated list" format below.
     *       This is good anyway because there are different conventions for release names
     *       (e.g. "1.0.0" or "1.0.0.GA" or "1.0.0.2017-12") and any automation here is likely to cause surprises.
     *   <li>Comma-separated list of quoted {@code name:versionRange}. For example,
     *       {@code "org.example.mybundle:[0,1)","org.example.myotherbundle:[0,1)"} (useful for
     *       when this bundle merges the contents of two previous bundles), or
     *       {@code "*","org.example.mybundle:1.0.0.SNAPSHOT","org.example.mybundle:1.0.0.rc1"}
     *       when releasing {@code org.example.mybundle:1.0.0.GA} 
     *       (to replace versions pre 1.0.0 as well as a snapshot and RC1)
     *   <li>{@code "*"} means all lower versions of this bundle. For example, if the bundle is 
     *       {@code org.example.mybundle:1.0.0}, then it is equivalent to writing 
     *       {@code "org.example.mybundle:[0,1.0.0)"}
     * </ul>
     */
    @Beta
    public static final String MANIFEST_HEADER_FORCE_REMOVE_BUNDLES = "brooklyn-catalog-force-remove-bundles";
    
    /**
     * The result from parsing bundle(s) to find their upgrade info.
     */
    public static class CatalogUpgrades {
        public static final CatalogUpgrades EMPTY = new CatalogUpgrades(builder());
        
        public static class Builder {
            private Set<VersionRangedName> removedLegacyItems = new LinkedHashSet<>();
            private Set<VersionRangedName> removedBundles = new LinkedHashSet<>();

            public Builder removedLegacyItems(Collection<VersionRangedName> vals) {
                removedLegacyItems.addAll(vals);
                return this;
            }
            public Builder removedBundles(Collection<VersionRangedName> vals) {
                removedBundles.addAll(vals);
                return this;
            }
            public Builder addAll(CatalogUpgrades other) {
                removedLegacyItems.addAll(other.removedLegacyItems);
                removedBundles.addAll(other.removedBundles);
                return this;
            }
            public CatalogUpgrades build() {
                return new CatalogUpgrades(this);
            }
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        private final Set<VersionRangedName> removedLegacyItems;
        private final Set<VersionRangedName> removedBundles;
        
        public CatalogUpgrades(Builder builder) {
            this.removedLegacyItems = ImmutableSet.copyOf(builder.removedLegacyItems);
            this.removedBundles = ImmutableSet.copyOf(builder.removedBundles);
        }

        public boolean isEmpty() {
            return removedLegacyItems.isEmpty() && removedBundles.isEmpty();
        }

        public Set<VersionRangedName> getRemovedLegacyItems() {
            return removedLegacyItems;
        }
        
        public Set<VersionRangedName> getRemovedBundles() {
            return removedBundles;
        }

        public boolean isLegacyItemRemoved(CatalogItem<?, ?> legacyCatalogItem) {
            VersionedName name = new VersionedName(legacyCatalogItem.getSymbolicName(), legacyCatalogItem.getVersion());
            return contains(removedLegacyItems, name);
        }

        public boolean isBundleRemoved(VersionedName bundle) {
            return contains(removedBundles, bundle);
        }
        
        public boolean contains(Iterable<VersionRangedName> names, VersionedName name) {
            for (VersionRangedName contender : names) {
                if (contender.getSymbolicName().equals(name.getSymbolicName()) && contender.getOsgiVersionRange().includes(name.getOsgiVersion())) {
                    return true;
                }
            }
            return false;
        }
    }
    
    /**
     * Records a name (string) and version range (string),
     * with conveniences for pretty-printing and converting to OSGi format.
     * 
     * Implementation-wise, this is similar to {@link VersionedName}, but is intended
     * as internal-only so is cut down to only what is needed.
     * 
     * Both the name and the version range are required.
     */
    public static class VersionRangedName {
        private final String name;
        private final String v;
        private transient volatile VersionRange cachedOsgiVersionRange;

        public static VersionRangedName fromString(String val, boolean singleVersionIsOsgiRange) {
            if (Strings.isBlank(val)) {
                throw new IllegalArgumentException("Must not be blank");
            }
            String[] parts = val.split(":");
            if (parts.length > 2) {
                throw new IllegalArgumentException("Identifier '"+val+"' has too many parts; max one ':' symbol");
            }
            if (parts.length == 1 || Strings.isBlank(parts[1])) {
                throw new IllegalArgumentException("Identifier '"+val+"' must be of 'name:versionRange' syntax");
            } else if (singleVersionIsOsgiRange || (parts[1].startsWith("(") || parts[1].startsWith("["))) {
                return new VersionRangedName(parts[0], parts[1]);
            } else {
                return new VersionRangedName(parts[0], "["+parts[1]+","+parts[1]+"]");
            }
        }

        public VersionRangedName(String name, VersionRange v) {
            this.name = checkNotNull(name, "name").toString();
            this.v = checkNotNull(v, "versionRange").toString();
        }
        
        private VersionRangedName(String name, String v) {
            this.name = checkNotNull(name, "name");
            this.v = checkNotNull(v, "versionRange");
        }
        
        @Override
        public String toString() {
            return name + ":" + v;
        }
        
        public String toOsgiString() {
            return name + ":" + getOsgiVersionRange();
        }

        public String getSymbolicName() {
            return name;
        }

        public VersionRange getOsgiVersionRange() {
            if (cachedOsgiVersionRange == null) {
                cachedOsgiVersionRange = VersionRange.valueOf(BrooklynVersionSyntax.toValidOsgiVersionRange(v));
            }
            return cachedOsgiVersionRange;
        }

        public String getVersionString() {
            return v;
        }
        
        @Override
        public int hashCode() {
            return Objects.hashCode(name, v);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof VersionRangedName)) {
                return false;
            }
            VersionRangedName o = (VersionRangedName) other;
            return Objects.equal(name, o.name) && Objects.equal(v, o.v);
        }
    }

    public static CatalogUpgrades parseBundleManifestForCatalogUpgrades(Bundle bundle, Supplier<? extends Iterable<? extends RegisteredType>> typeSupplier) {
        // TODO Add support for the other options described in the proposal:
        //   https://docs.google.com/document/d/1Lm47Kx-cXPLe8BO34-qrL3ZMPosuUHJILYVQUswEH6Y/edit#
        //   section "Bundle Upgrade Metadata"
        
        Dictionary<String, String> headers = bundle.getHeaders();
        return CatalogUpgrades.builder()
                .removedLegacyItems(parseForceRemoveLegacyItemsHeader(headers.get(MANIFEST_HEADER_FORCE_REMOVE_LEGACY_ITEMS), bundle, typeSupplier))
                .removedBundles(parseForceRemoveBundlesHeader(headers.get(MANIFEST_HEADER_FORCE_REMOVE_BUNDLES), bundle))
                .build();
    }

    @VisibleForTesting
    static List<VersionRangedName> parseForceRemoveLegacyItemsHeader(String input, Bundle bundle, Supplier<? extends Iterable<? extends RegisteredType>> typeSupplier) {
        if (input == null) return ImmutableList.of();
        if (stripQuotes(input.trim()).equals("*")) {
            VersionRange versionRange = VersionRange.valueOf("[0,"+bundle.getVersion()+")");
            List<VersionRangedName> result = new ArrayList<>();
            for (RegisteredType item : typeSupplier.get()) {
                result.add(new VersionRangedName(item.getSymbolicName(), versionRange));
            }
            return result;
        } else {
            return parseVersionRangedNameList(input, false);
        }
    }
    

    @VisibleForTesting
    static List<VersionRangedName> parseForceRemoveBundlesHeader(String input, Bundle bundle) {
        if (input == null) return ImmutableList.of();
        if (stripQuotes(input.trim()).equals("*")) {
            String bundleVersion = bundle.getVersion().toString();
            String maxVersion;
            if (BrooklynVersionSyntax.isSnapshot(bundleVersion)) {
                maxVersion = BrooklynVersionSyntax.stripSnapshot(bundleVersion);
            } else {
                maxVersion = bundleVersion;
            }
            return ImmutableList.of(new VersionRangedName(bundle.getSymbolicName(), "[0,"+maxVersion+")"));
        } else {
            return parseVersionRangedNameList(input, false);
        }
    }
    
    @VisibleForTesting
    static List<VersionRangedName> parseVersionRangedNameList(String input, boolean singleVersionIsOsgiRange) {
        if (input == null) return ImmutableList.of();
        
        List<String> vals = QuotedStringTokenizer.builder()
                .delimiterChars(",")
                .includeQuotes(false)
                .includeDelimiters(false)
                .buildList(input);
        
        List<VersionRangedName> versionedItems = new ArrayList<>();
        for (String val : vals) {
            try {
                versionedItems.add(VersionRangedName.fromString(val.trim(), singleVersionIsOsgiRange));
            } catch (Exception e) {
                if (Strings.containsAny(val, "(", ")", "[", "]") &&
                        !Strings.containsAny(val, "'", "\"")) {
                    throw Exceptions.propagateAnnotated("Entry cannot be parsed. If defining a range on an entry you must quote the entry.", e);
                }
                throw Exceptions.propagate(e);
            }
        }
        return versionedItems;
    }
    
    @VisibleForTesting
    static String stripQuotes(String input) {
        String quoteChars = QuotedStringTokenizer.DEFAULT_QUOTE_CHARS;
        boolean quoted = (input.length() >= 2) && quoteChars.contains(input.substring(0, 1))
                && quoteChars.contains(input.substring(input.length() - 1));
        return (quoted ? input.substring(1, input.length() - 1) : input);
    }
}
