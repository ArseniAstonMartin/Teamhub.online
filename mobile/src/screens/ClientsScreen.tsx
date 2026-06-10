import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  RefreshControl,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';

import { colors } from '../theme/colors';
import type { RootStackParamList } from '../navigation/types';
import { useCustomers, type CustomerSummary } from '../api/customersApi';

type NavProp = NativeStackNavigationProp<RootStackParamList>;

// ─── Helpers ─────────────────────────────────────────────────────────────────

function formatDate(iso: string) {
  const d = new Date(iso);
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}

function buildAddressLine(customer: CustomerSummary) {
  return [customer.addressLine, customer.city, customer.state].filter(Boolean).join(', ');
}

// ─── Skeleton ────────────────────────────────────────────────────────────────

function SkeletonRow() {
  return (
    <View style={styles.skeletonCard} testID="skeleton-row">
      <View style={[styles.skeletonLine, { width: '55%' }]} />
      <View style={[styles.skeletonLine, { width: '80%', marginTop: 6 }]} />
      <View style={[styles.skeletonLine, { width: '40%', marginTop: 6 }]} />
    </View>
  );
}

// ─── Customer row ─────────────────────────────────────────────────────────────

interface CustomerRowProps {
  customer: CustomerSummary;
  onPress: (id: string) => void;
}

function CustomerRow({ customer, onPress }: CustomerRowProps) {
  const address = buildAddressLine(customer);
  return (
    <Pressable
      style={({ pressed }) => [styles.card, pressed && styles.cardPressed]}
      onPress={() => onPress(customer.id)}
      accessibilityRole="button"
      accessibilityLabel={customer.name}
      testID={`customer-row-${customer.id}`}
    >
      <Text style={styles.customerName} numberOfLines={1}>
        {customer.name}
      </Text>
      {address ? (
        <Text style={styles.addressText} numberOfLines={1}>
          {address}
        </Text>
      ) : null}
      <Text style={styles.dateText}>Last job: {formatDate(customer.updatedAt)}</Text>
    </Pressable>
  );
}

// ─── Main component ───────────────────────────────────────────────────────────

export default function ClientsScreen() {
  const navigation = useNavigation<NavProp>();

  const [inputValue, setInputValue] = useState('');
  const [search, setSearch] = useState('');
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const handleSearchChange = useCallback((text: string) => {
    setInputValue(text);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      setSearch(text);
    }, 300);
  }, []);

  useEffect(() => {
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, []);

  const { data, isLoading, isFetchingNextPage, hasNextPage, fetchNextPage, refetch, isRefetching } =
    useCustomers(search);

  const customers = useMemo(
    () => data?.pages.flatMap((p) => p.content) ?? [],
    [data],
  );

  const handleRowPress = useCallback(
    (customerId: string) => {
      navigation.navigate('CustomerDetail', { customerId });
    },
    [navigation],
  );

  const handleEndReached = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) {
      void fetchNextPage();
    }
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  const renderItem = useCallback(
    ({ item }: { item: CustomerSummary }) => (
      <CustomerRow customer={item} onPress={handleRowPress} />
    ),
    [handleRowPress],
  );

  const keyExtractor = useCallback((item: CustomerSummary) => item.id, []);

  const ListFooter = isFetchingNextPage ? (
    <ActivityIndicator style={styles.footerSpinner} color={colors.primary} />
  ) : null;

  const ListEmpty = !isLoading ? (
    <View style={styles.emptyState} testID="empty-state">
      <Text style={styles.emptyText}>No clients yet</Text>
    </View>
  ) : null;

  return (
    <View style={styles.container}>
      {/* Search bar */}
      <View style={styles.searchWrapper}>
        <TextInput
          style={styles.searchInput}
          placeholder="Search customers..."
          placeholderTextColor={colors.subtext}
          value={inputValue}
          onChangeText={handleSearchChange}
          autoCapitalize="none"
          autoCorrect={false}
          testID="search-input"
          returnKeyType="search"
          clearButtonMode="while-editing"
        />
      </View>

      {/* Skeleton during initial load */}
      {isLoading && customers.length === 0 ? (
        <View style={styles.skeletonList}>
          {[0, 1, 2, 3, 4].map((i) => (
            <SkeletonRow key={i} />
          ))}
        </View>
      ) : (
        <FlatList
          data={customers}
          keyExtractor={keyExtractor}
          renderItem={renderItem}
          contentContainerStyle={styles.listContent}
          onEndReached={handleEndReached}
          onEndReachedThreshold={0.3}
          ListEmptyComponent={ListEmpty}
          ListFooterComponent={ListFooter}
          refreshControl={
            <RefreshControl
              refreshing={isRefetching && !isFetchingNextPage}
              onRefresh={refetch}
              tintColor={colors.primary}
            />
          }
        />
      )}
    </View>
  );
}

// ─── Styles ───────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  searchWrapper: {
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  searchInput: {
    height: 40,
    backgroundColor: colors.card,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: colors.border,
    paddingHorizontal: 14,
    fontSize: 15,
    color: colors.text,
  },
  listContent: {
    paddingHorizontal: 16,
    paddingTop: 8,
    paddingBottom: 32,
    gap: 8,
    flexGrow: 1,
  },
  card: {
    backgroundColor: colors.card,
    borderRadius: 12,
    padding: 14,
    borderWidth: 1,
    borderColor: colors.border,
    gap: 3,
  },
  cardPressed: {
    opacity: 0.7,
  },
  customerName: {
    fontSize: 16,
    fontWeight: '700',
    color: colors.text,
  },
  addressText: {
    fontSize: 13,
    color: colors.subtext,
  },
  dateText: {
    fontSize: 12,
    color: colors.tabInactive,
    marginTop: 2,
  },
  emptyState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingTop: 80,
  },
  emptyText: {
    fontSize: 16,
    color: colors.subtext,
  },
  footerSpinner: {
    paddingVertical: 16,
  },
  skeletonList: {
    paddingHorizontal: 16,
    paddingTop: 8,
    gap: 8,
  },
  skeletonCard: {
    backgroundColor: colors.card,
    borderRadius: 12,
    padding: 14,
    borderWidth: 1,
    borderColor: colors.border,
    height: 80,
  },
  skeletonLine: {
    height: 12,
    backgroundColor: colors.border,
    borderRadius: 6,
  },
});
